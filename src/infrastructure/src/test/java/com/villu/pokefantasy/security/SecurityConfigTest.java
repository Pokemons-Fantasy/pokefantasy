package com.villu.pokefantasy.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
}
