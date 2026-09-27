package com.villu.pokefantasy.auth;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Cookies de sesión: {@value #ACCESS} (JWT de vida corta) y {@value #REFRESH} (token opaco revocable).
 * Ambas httpOnly y {@code SameSite=None; Secure} porque el frontend vive en otro dominio (Netlify).
 */
public final class AuthCookies {

    public static final String ACCESS = "jwt";
    public static final String REFRESH = "refresh";

    private AuthCookies() {}

    /** Valor de cabecera {@code Set-Cookie} para {@code name}. */
    public static String set(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString();
    }

    /** Valor de cabecera {@code Set-Cookie} que borra la cookie {@code name}. */
    public static String clear(String name) {
        return set(name, "", Duration.ZERO);
    }
}
