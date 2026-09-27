package com.villu.pokefantasy.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRedisAdapterTest {

    private static final Duration TTL = Duration.ofDays(30);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SetOperations<String, String> setOps;

    private RefreshTokenRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        adapter = new RefreshTokenRedisAdapter(redisTemplate, 30);
    }

    @Test
    void issue_storesHashOfTokenWithTtl_neverTheTokenItself() {
        String token = adapter.issue("ash");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), eq("ash"), eq(TTL));
        assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 32 bytes en base64url
        assertThat(key.getValue())
                .isEqualTo(RefreshTokenRedisAdapter.key(token))
                .startsWith(RefreshTokenRedisAdapter.PREFIX)
                .doesNotContain(token);
        // Y queda apuntado entre las sesiones del usuario, con la misma caducidad.
        verify(setOps).add("refresh-user:ash", key.getValue());
        verify(redisTemplate).expire("refresh-user:ash", TTL);
    }

    @Test
    void issue_generatesDifferentTokensEachTime() {
        assertThat(adapter.issue("ash")).isNotEqualTo(adapter.issue("ash"));
    }

    @Test
    void issue_redisDown_returnsNull() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThat(adapter.issue("ash")).isNull();
    }

    @Test
    void resolve_validToken_returnsUserAndSlidesExpiry() {
        String key = RefreshTokenRedisAdapter.key("tok");
        when(valueOps.get(key)).thenReturn("ash");

        assertThat(adapter.resolve("tok")).contains("ash");
        verify(redisTemplate).expire(key, TTL);
        verify(redisTemplate).expire("refresh-user:ash", TTL);
    }

    @Test
    void resolve_unknownToken_empty() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(adapter.resolve("tok")).isEmpty();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void resolve_redisDown_failsClosed() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(adapter.resolve("tok")).isEmpty();
    }

    @Test
    void revoke_deletesKey() {
        adapter.revoke("tok");

        verify(redisTemplate).delete(RefreshTokenRedisAdapter.key("tok"));
    }

    @Test
    void revoke_redisDown_doesNotThrow() {
        when(redisTemplate.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> adapter.revoke("tok")).doesNotThrowAnyException();
    }

    @Test
    void revokeAll_deletesEverySessionOfTheUser() {
        Set<String> keys = Set.of("refresh:a", "refresh:b");
        when(setOps.members("refresh-user:ash")).thenReturn(keys);

        adapter.revokeAll("ash");

        verify(redisTemplate).delete(keys);
        verify(redisTemplate).delete("refresh-user:ash");
    }

    @Test
    void revokeAll_noSessions_onlyClearsTheIndex() {
        when(setOps.members("refresh-user:ash")).thenReturn(Set.of());

        adapter.revokeAll("ash");

        verify(redisTemplate, never()).delete(anyCollection());
        verify(redisTemplate).delete("refresh-user:ash");
    }

    @Test
    void ttl_comesFromConfiguration() {
        assertThat(adapter.ttl()).isEqualTo(TTL);
    }
}
