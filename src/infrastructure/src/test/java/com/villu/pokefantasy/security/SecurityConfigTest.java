package com.villu.pokefantasy.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Test
    void apiDocsAndSwaggerUiArePublic_restIsNot() {
        assertThat(SecurityConfig.isApiDocs("/v3/api-docs")).isTrue();
        assertThat(SecurityConfig.isApiDocs("/v3/api-docs/swagger-config")).isTrue();
        assertThat(SecurityConfig.isApiDocs("/swagger-ui/index.html")).isTrue();
        assertThat(SecurityConfig.isApiDocs("/swagger-ui.html")).isTrue();
        assertThat(SecurityConfig.isApiDocs("/v1/leagues/my")).isFalse();
        assertThat(SecurityConfig.isApiDocs("/actuator/metrics")).isFalse();
    }

    @Test
    void entryPoint_delegatesToTheMvcExceptionHandler() throws Exception {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        InsufficientAuthenticationException exception = new InsufficientAuthenticationException("x");
        when(resolver.resolveException(request, response, null, exception)).thenReturn(new ModelAndView());

        SecurityConfig.problemDetailEntryPoint(resolver).commence(request, response, exception);

        verify(resolver).resolveException(request, response, null, exception);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    void entryPoint_fallsBackToPlain401_whenTheHandlerCannotWriteTheResponse() throws Exception {
        // p. ej. un EventSource (Accept: text/event-stream) sin sesión
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(null);

        SecurityConfig.problemDetailEntryPoint(resolver)
                .commence(mock(HttpServletRequest.class), response, new InsufficientAuthenticationException("x"));

        verify(response).sendError(401);
    }

    @Test
    void csrfRejection_delegatesToTheMvcExceptionHandler() throws Exception {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        MissingCsrfTokenException exception = new MissingCsrfTokenException(null);
        when(resolver.resolveException(request, response, null, exception)).thenReturn(new ModelAndView());

        SecurityConfig.accessDeniedHandler(resolver).handle(request, response, exception);

        verify(resolver).resolveException(request, response, null, exception);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    void csrfRejection_fallsBackToPlain403_whenTheHandlerCannotWriteTheResponse() throws Exception {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(null);

        SecurityConfig.accessDeniedHandler(resolver)
                .handle(new MockHttpServletRequest(), response, new MissingCsrfTokenException(null));

        verify(response).sendError(eq(403), any());
    }

    @Test
    void otherAccessDenials_keepTheDefault403() throws Exception {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        SecurityConfig.accessDeniedHandler(resolver)
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        verifyNoInteractions(resolver);
        verify(response).sendError(eq(403), any());
    }

    @Test
    void csrfTokenRepository_keepsNothing() {
        SecurityConfig.StatelessCsrfTokenRepository repository = new SecurityConfig.StatelessCsrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, response);

        assertThat(token.getToken()).isNotBlank();
        assertThat(repository.loadToken(request)).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void cors_allowsTheWebItsDeployPreviewsTheAndroidAppAndLocalDevelopment() {
        CorsConfiguration cors = corsConfiguration();

        assertThat(List.of(
                "https://pokefantasy.netlify.app",
                "https://deploy-preview-89--pokefantasy.netlify.app",
                "https://localhost",
                "capacitor://localhost",
                "http://localhost:5173",
                "http://127.0.0.1:8080"))
                .allSatisfy(origin -> assertThat(cors.checkOrigin(origin)).isEqualTo(origin));
    }

    @Test
    void cors_rejectsOtherNetlifySitesAndLookalikes() {
        // Cualquiera publica gratis en *.netlify.app: con credenciales, leería la API con la sesión de la víctima.
        CorsConfiguration cors = corsConfiguration();

        assertThat(Arrays.asList(
                "https://atacante.netlify.app",
                "https://deploy-preview-1--otro.netlify.app",
                "https://deploy-preview-x--pokefantasy.netlify.app",
                "https://deploy-preview-1--pokefantasy.netlify.app.evil.com",
                "https://evil.com/https://deploy-preview-1--pokefantasy.netlify.app",
                "http://pokefantasy.netlify.app",
                "https://pokefantasy.onrender.com",
                "",
                null))
                .allSatisfy(origin -> assertThat(cors.checkOrigin(origin)).isNull());
    }

    @Test
    void cors_sendsCredentials() {
        assertThat(corsConfiguration().getAllowCredentials()).isTrue();
    }

    private static CorsConfiguration corsConfiguration() {
        return new SecurityConfig().corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/v1/leagues/my"));
    }
}
