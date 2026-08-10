package com.villu.pokefantasy.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes());

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", SECRET);
        ReflectionTestUtils.setField(provider, "expirationMs", 3_600_000L);
    }

    @Test
    void generateToken_thenExtractUsername_roundTrips() {
        String token = provider.generateToken("ash");

        assertThat(provider.extractUsername(token)).isEqualTo("ash");
    }

    @Test
    void isTokenValid_matchingUsernameAndNotExpired_returnsTrue() {
        String token = provider.generateToken("ash");

        assertThat(provider.isTokenValid(token, "ash")).isTrue();
    }

    @Test
    void isTokenValid_differentUsername_returnsFalse() {
        String token = provider.generateToken("ash");

        assertThat(provider.isTokenValid(token, "brock")).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(provider, "expirationMs", -1_000L);
        String token = provider.generateToken("ash");

        assertThat(provider.isTokenValid(token, "ash")).isFalse();
    }

    @Test
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(provider.isTokenValid("not-a-jwt", "ash")).isFalse();
    }

    @Test
    void extractUsername_malformedToken_returnsNull() {
        assertThat(provider.extractUsername("not-a-jwt")).isNull();
    }

    @Test
    void extractUsername_tokenSignedWithDifferentKey_returnsNull() {
        JwtTokenProvider otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otherProvider, "secret",
                Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes()));
        ReflectionTestUtils.setField(otherProvider, "expirationMs", 3_600_000L);
        String tokenFromOtherKey = otherProvider.generateToken("ash");

        assertThat(provider.extractUsername(tokenFromOtherKey)).isNull();
    }
}
