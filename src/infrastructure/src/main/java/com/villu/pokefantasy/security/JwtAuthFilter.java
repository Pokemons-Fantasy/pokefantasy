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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Autentica con el JWT de acceso (cookie {@code jwt} o cabecera Bearer). Si falta o ha caducado y hay
 * una cookie {@code refresh} vigente, emite un JWT nuevo en la misma respuesta: el cliente no tiene
 * que hacer nada para renovar la sesión.
 */
@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenPort tokenPort;
    private final RefreshTokenPort refreshTokenPort;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(TokenPort tokenPort, RefreshTokenPort refreshTokenPort,
                         UserDetailsService userDetailsService) {
        this.tokenPort = tokenPort;
        this.refreshTokenPort = refreshTokenPort;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cookie(request, AuthCookies.ACCESS);
            if (token == null) {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    token = header.substring(7);
                }
            }

            boolean authenticated = token != null && authenticateWithAccessToken(token, request);
            if (!authenticated) {
                String refreshToken = cookie(request, AuthCookies.REFRESH);
                if (refreshToken != null) {
                    authenticateWithRefreshToken(refreshToken, request, response);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean authenticateWithAccessToken(String token, HttpServletRequest request) {
        try {
            String username = tokenPort.extractUsername(token);
            if (username == null) {
                return false;
            }
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!tokenPort.isTokenValid(token, userDetails.getUsername())) {
                return false;
            }
            authenticate(userDetails, request);
            return true;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
            SecurityContextHolder.clearContext();
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
        authenticate(userDetails, request);
    }

    private static void authenticate(UserDetails userDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
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
