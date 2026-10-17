package com.villu.pokefantasy.security;

import com.villu.pokefantasy.auth.AuthCookies;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Set;

/**
 * Qué peticiones rechaza el {@code CsrfFilter} (ADR-018): las que cambian estado, llevan cookie de sesión
 * y no traen {@value #HEADER}.
 * <ul>
 *   <li>Basta una cabecera propia: otra web no puede añadirla sin un preflight, y el preflight lo corta el
 *       CORS de orígenes exactos de {@link SecurityConfig}.</li>
 *   <li>Solo con cookie: es la credencial que el navegador manda solo. Con {@code Authorization: Bearer}
 *       no hay nada que una web ajena pueda aprovechar.</li>
 * </ul>
 */
public final class CsrfHeaderRequestMatcher implements RequestMatcher {

    public static final String HEADER = "X-Requested-With";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> SESSION_COOKIES = Set.of(AuthCookies.ACCESS, AuthCookies.REFRESH);

    @Override
    public boolean matches(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod()) || !hasSessionCookie(request)) {
            return false;
        }
        String header = request.getHeader(HEADER);
        return header == null || header.isBlank();
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIES.contains(cookie.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
