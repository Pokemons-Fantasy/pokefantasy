package com.villu.pokefantasy.security;

import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock TokenPort tokenPort;
    @Mock RefreshTokenPort refreshTokenPort;
    @Mock UserDetailsService userDetailsService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(tokenPort, refreshTokenPort, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_withCookie_authenticatesUser() throws Exception {
        Cookie jwtCookie = new Cookie("jwt", "valid-token");
        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(tokenPort.extractUsername("valid-token")).thenReturn("ash");
        when(tokenPort.isTokenValid("valid-token", "ash")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("ash"))
                .thenReturn(new User("ash", "", Collections.emptyList()));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ash");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_withBearerHeader_authenticatesUser() throws Exception {
        when(request.getCookies()).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(tokenPort.extractUsername("valid-token")).thenReturn("ash");
        when(tokenPort.isTokenValid("valid-token", "ash")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("ash"))
                .thenReturn(new User("ash", "", Collections.emptyList()));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_withNoTokenAtAll_doesNotAuthenticate() throws Exception {
        when(request.getCookies()).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verifyNoInteractions(tokenPort);
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    private void stubRefreshSucceeds() {
        when(refreshTokenPort.resolve("refresh-token")).thenReturn(Optional.of("ash"));
        when(userDetailsService.loadUserByUsername("ash"))
                .thenReturn(new User("ash", "", Collections.emptyList()));
        when(tokenPort.generateToken("ash")).thenReturn("new-access");
        when(tokenPort.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(refreshTokenPort.ttl()).thenReturn(Duration.ofDays(30));
    }

    @Test
    void doFilter_accessCookieGoneButRefreshValid_issuesNewAccessTokenAndAuthenticates() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refresh", "refresh-token")});
        stubRefreshSucceeds();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ash");
        verify(response).addHeader(eq("Set-Cookie"), startsWith("jwt=new-access;"));
        verify(response).addHeader(eq("Set-Cookie"), startsWith("refresh=refresh-token;"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_expiredAccessTokenAndRefreshValid_refreshes() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("jwt", "expired"), new Cookie("refresh", "refresh-token")});
        when(tokenPort.extractUsername("expired")).thenReturn(null); // firma válida pero caducado
        stubRefreshSucceeds();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ash");
        verify(response).addHeader(eq("Set-Cookie"), startsWith("jwt=new-access;"));
    }

    @Test
    void doFilter_validAccessToken_doesNotTouchRefreshToken() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("jwt", "valid-token"), new Cookie("refresh", "refresh-token")});
        when(tokenPort.extractUsername("valid-token")).thenReturn("ash");
        when(tokenPort.isTokenValid("valid-token", "ash")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("ash"))
                .thenReturn(new User("ash", "", Collections.emptyList()));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verifyNoInteractions(refreshTokenPort);
        verify(response, never()).addHeader(anyString(), anyString());
    }

    @Test
    void doFilter_invalidAccessTokenForOtherUser_fallsBackToRefresh() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("jwt", "stale"), new Cookie("refresh", "refresh-token")});
        when(tokenPort.extractUsername("stale")).thenReturn("ash");
        when(tokenPort.isTokenValid("stale", "ash")).thenReturn(false);
        stubRefreshSucceeds();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ash");
    }

    @Test
    void doFilter_revokedRefreshToken_staysAnonymous() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refresh", "revoked")});
        when(request.getHeader("Authorization")).thenReturn(null);
        when(refreshTokenPort.resolve("revoked")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(response, never()).addHeader(anyString(), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_refreshTokenOfDeletedUser_revokesItAndStaysAnonymous() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refresh", "orphan")});
        when(refreshTokenPort.resolve("orphan")).thenReturn(Optional.of("gone"));
        when(userDetailsService.loadUserByUsername("gone")).thenThrow(new UsernameNotFoundException("gone"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(refreshTokenPort).revoke("orphan");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_alreadyAuthenticated_skipsTokens() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("ash", null,
                        Collections.emptyList()));

        filter.doFilterInternal(request, response, chain);

        verifyNoInteractions(tokenPort, refreshTokenPort);
        verify(chain).doFilter(request, response);
    }
}
