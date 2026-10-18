package com.villu.pokefantasy.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfHeaderRequestMatcherTest {

    private final CsrfHeaderRequestMatcher matcher = new CsrfHeaderRequestMatcher();

    private static MockHttpServletRequest request(String method, Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/v1/leagues/invite/x/redeem");
        if (cookies.length > 0) {
            request.setCookies(cookies);
        }
        return request;
    }

    private static Cookie session() {
        return new Cookie("jwt", "token");
    }

    @Test
    void postWithSessionCookieAndNoHeader_requiresProtection() {
        assertThat(matcher.matches(request("POST", session()))).isTrue();
    }

    @Test
    void postWithOnlyRefreshCookieAndNoHeader_requiresProtection() {
        // El jwt caducó y el navegador lo descartó: el filtro renovaría la sesión con el refresh.
        assertThat(matcher.matches(request("POST", new Cookie("refresh", "r")))).isTrue();
    }

    @Test
    void postWithSessionCookieAndBlankHeader_requiresProtection() {
        MockHttpServletRequest request = request("POST", session());
        request.addHeader(CsrfHeaderRequestMatcher.HEADER, "  ");

        assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    void postWithSessionCookieAndHeader_doesNotRequireProtection() {
        MockHttpServletRequest request = request("POST", session());
        request.addHeader(CsrfHeaderRequestMatcher.HEADER, "XMLHttpRequest");

        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void putAndDeleteWithSessionCookieAndNoHeader_requireProtection() {
        assertThat(matcher.matches(request("PUT", session()))).isTrue();
        assertThat(matcher.matches(request("DELETE", session()))).isTrue();
    }

    @Test
    void getWithSessionCookieAndNoHeader_doesNotRequireProtection() {
        assertThat(matcher.matches(request("GET", session()))).isFalse();
        assertThat(matcher.matches(request("HEAD", session()))).isFalse();
        assertThat(matcher.matches(request("OPTIONS", session()))).isFalse();
    }

    @Test
    void postWithoutCookies_doesNotRequireProtection() {
        // Con Bearer no hay credencial que el navegador mande solo: otra web no puede usarla.
        MockHttpServletRequest request = request("POST");
        request.addHeader("Authorization", "Bearer x");

        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void postWithUnrelatedCookie_doesNotRequireProtection() {
        assertThat(matcher.matches(request("POST", new Cookie("pf-theme", "dark")))).isFalse();
    }
}
