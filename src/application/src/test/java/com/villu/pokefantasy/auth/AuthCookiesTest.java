package com.villu.pokefantasy.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookiesTest {

    @Test
    void set_buildsCrossSiteHttpOnlyCookie() {
        String header = AuthCookies.set(AuthCookies.REFRESH, "abc", Duration.ofDays(30));

        assertThat(header)
                .startsWith("refresh=abc;")
                .contains("Path=/", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=None");
    }

    @Test
    void clear_expiresCookieImmediately() {
        assertThat(AuthCookies.clear(AuthCookies.ACCESS))
                .startsWith("jwt=;")
                .contains("Max-Age=0");
    }
}
