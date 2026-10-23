package com.villu.pokefantasy.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeletedUsernameRedisAdapterTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final DeletedUsernameRedisAdapter adapter = new DeletedUsernameRedisAdapter(redisTemplate);

    @Test
    void reserve_storesTheLowercasedNameWithTheTtl() {
        when(redisTemplate.opsForValue()).thenReturn(values);

        adapter.reserve("Ash", Duration.ofMinutes(15));

        verify(values).set("deleted-user:ash", "1", Duration.ofMinutes(15));
    }

    @Test
    void isReserved_ignoresCase() {
        when(redisTemplate.hasKey("deleted-user:ash")).thenReturn(true);

        assertThat(adapter.isReserved("ASH")).isTrue();
        assertThat(adapter.isReserved("misty")).isFalse();
    }

    @Test
    void redisDown_failsClosed() {
        // Sin reserva, un JWT vivo de la cuenta borrada entraría en una cuenta nueva con el mismo nombre: el
        // borrado se aborta (y su transacción se deshace) y el registro de ese nombre se rechaza.
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(redisTemplate.hasKey("deleted-user:ash")).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> adapter.reserve("ash", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(adapter.isReserved("ash")).isTrue();
    }
}
