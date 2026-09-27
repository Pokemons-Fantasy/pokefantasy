package com.villu.pokefantasy.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptRedisAdapterTest {

    private static final String KEY = "user:ash";
    private static final String REDIS_KEY = LoginAttemptRedisAdapter.PREFIX + KEY;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private LoginAttemptRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new LoginAttemptRedisAdapter(redisTemplate);
    }

    @Test
    void failureCount_noKey_returnsZero() {
        when(valueOps.get(REDIS_KEY)).thenReturn(null);

        assertThat(adapter.failureCount(KEY)).isZero();
    }

    @Test
    void failureCount_existingKey_returnsStoredValue() {
        when(valueOps.get(REDIS_KEY)).thenReturn("4");

        assertThat(adapter.failureCount(KEY)).isEqualTo(4);
    }

    @Test
    void failureCount_redisDown_failsOpen() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(adapter.failureCount(KEY)).isZero();
    }

    @Test
    void recordFailure_firstFailure_opensWindow() {
        when(valueOps.increment(REDIS_KEY)).thenReturn(1L);
        when(redisTemplate.getExpire(REDIS_KEY)).thenReturn(-1L);

        adapter.recordFailure(KEY, WINDOW);

        verify(redisTemplate).expire(REDIS_KEY, WINDOW);
    }

    @Test
    void recordFailure_laterFailureWithTtl_keepsExistingWindow() {
        when(valueOps.increment(REDIS_KEY)).thenReturn(3L);
        when(redisTemplate.getExpire(REDIS_KEY)).thenReturn(600L);

        adapter.recordFailure(KEY, WINDOW);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void recordFailure_counterLostItsTtl_restoresWindow() {
        // Si el EXPIRE se perdió, el contador no puede quedarse bloqueando para siempre.
        when(valueOps.increment(REDIS_KEY)).thenReturn(6L);
        when(redisTemplate.getExpire(REDIS_KEY)).thenReturn(-1L);

        adapter.recordFailure(KEY, WINDOW);

        verify(redisTemplate).expire(REDIS_KEY, WINDOW);
    }

    @Test
    void recordFailure_redisDown_doesNotThrow() {
        when(valueOps.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> adapter.recordFailure(KEY, WINDOW)).doesNotThrowAnyException();
    }

    @Test
    void clearFailures_deletesKey() {
        adapter.clearFailures(KEY);

        verify(redisTemplate).delete(REDIS_KEY);
    }

    @Test
    void clearFailures_redisDown_doesNotThrow() {
        when(redisTemplate.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> adapter.clearFailures(KEY)).doesNotThrowAnyException();
    }
}
