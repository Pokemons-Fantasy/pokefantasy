package com.villu.pokefantasy.security;

import com.villu.pokefantasy.auth.AuthCookies;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Autentica con el JWT de acceso (cookie {@code jwt} o cabecera Bearer). Si falta o ha caducado y hay
 * una cookie {@code refresh} vigente, emite un JWT nuevo en la misma respuesta: el cliente no tiene
 * que hacer nada para renovar la sesión.
 *
 * <p>La autenticación se guarda en el {@link SecurityContextRepository} (atributo de la petición, sin
 * sesión): los dispatch posteriores de la misma petición, como el ASYNC con que Tomcat cierra una
 * conexión SSE, no vuelven a pasar por este filtro y la recuperan de ahí.
 */
@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenPort tokenPort;
    private final RefreshTokenPort refreshTokenPort;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public JwtAuthFilter(TokenPort tokenPort, RefreshTokenPort refreshTokenPort,
                         UserDetailsService userDetailsService, SecurityContextRepository securityContextRepository) {
        this.tokenPort = tokenPort;
        this.refreshTokenPort = refreshTokenPort;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (securityContextHolderStrategy.getContext().getAuthentication() == null) {
            String token = cookie(request, AuthCookies.ACCESS);
            if (token == null) {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    token = header.substring(7);
                }
            }

            boolean authenticated = token != null && authenticateWithAccessToken(token, request, response);
            if (!authenticated) {
                String refreshToken = cookie(request, AuthCookies.REFRESH);
                if (refreshToken != null) {
                    authenticateWithRefreshToken(refreshToken, request, response);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean authenticateWithAccessToken(String token, HttpServletRequest request,
                                                HttpServletResponse response) {
        try {
            String username = tokenPort.extractUsername(token);
            if (username == null) {
                return false;
            }
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!tokenPort.isTokenValid(token, userDetails.getUsername())) {
                return false;
            }
            authenticate(userDetails, request, response);
            return true;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
            securityContextHolderStrategy.clearContext();
            log.debug("Ignoring invalid JWT authentication attempt", ignored);
            return false;
        }
    }

    private void authenticateWithRefreshToken(String refreshToken, HttpServletRequest request,
                                              HttpServletResponse response) {
        Optional<String> username = refreshTokenPort.resolve(refreshToken);
        if (username.isEmpty()) {
            return;
        }
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username.get());
        } catch (UsernameNotFoundException deleted) {
            refreshTokenPort.revoke(refreshToken);
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookies.set(AuthCookies.ACCESS,
                tokenPort.generateToken(userDetails.getUsername()), tokenPort.accessTokenTtl()));
        // Renueva también el Max-Age de la cookie: la caducidad deslizante está en Redis y en el navegador.
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookies.set(AuthCookies.REFRESH,
                refreshToken, refreshTokenPort.ttl()));
        authenticate(userDetails, request, response);
    }

    private void authenticate(UserDetails userDetails, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(auth);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (name.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
