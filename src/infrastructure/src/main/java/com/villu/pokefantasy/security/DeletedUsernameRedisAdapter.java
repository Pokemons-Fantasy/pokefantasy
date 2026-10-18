package com.villu.pokefantasy.security;

import com.villu.pokefantasy.ports.DeletedUsernamePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/** Claves {@code deleted-user:<nombre en minúsculas>} con la vida del JWT de acceso. */
@Component
@Slf4j
public class DeletedUsernameRedisAdapter implements DeletedUsernamePort {

    private static final String PREFIX = "deleted-user:";

    private final StringRedisTemplate redisTemplate;

    public DeletedUsernameRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void reserve(String username, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(username), "1", ttl);
        } catch (RuntimeException exception) {
            log.warn("Could not reserve deleted username {}: {}", username, exception.getMessage());
        }
    }

    @Override
    public boolean isReserved(String username) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(username)));
        } catch (RuntimeException exception) {
            log.warn("Could not check deleted username {}: {}", username, exception.getMessage());
            return false;
        }
    }

    private static String key(String username) {
        return PREFIX + username.toLowerCase(Locale.ROOT);
    }
}
