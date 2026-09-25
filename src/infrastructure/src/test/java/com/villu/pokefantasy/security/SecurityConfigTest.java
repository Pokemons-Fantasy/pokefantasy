package com.villu.pokefantasy.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
