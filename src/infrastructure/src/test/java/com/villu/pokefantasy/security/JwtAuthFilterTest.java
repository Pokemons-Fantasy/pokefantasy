package com.villu.pokefantasy.security;

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

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock TokenPort tokenPort;
    @Mock UserDetailsService userDetailsService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(tokenPort, userDetailsService);
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
}
