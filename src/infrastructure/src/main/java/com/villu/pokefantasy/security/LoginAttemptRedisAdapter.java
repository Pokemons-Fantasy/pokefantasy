package com.villu.pokefantasy.security;

import com.villu.pokefantasy.ports.LoginAttemptPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Contadores de login fallidos en Redis ({@code login-fail:<key>} con TTL = ventana) e IPs de confianza de cada
 * usuario ({@code login-trusted:<key>}).
 *
 * <p>Si Redis falla, no bloquea a nadie (fail-open): perder el límite unos minutos es preferible
 * a impedir el login de todos los usuarios.
 */
@Component
@Slf4j
public class LoginAttemptRedisAdapter implements LoginAttemptPort {

    static final String PREFIX = "login-fail:";
    static final String TRUSTED_PREFIX = "login-trusted:";

    private final StringRedisTemplate redisTemplate;

    public LoginAttemptRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long failureCount(String key) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException exception) {
            log.warn("Could not read login failures for {}: {}", key, exception.getMessage());
            return 0;
        }
    }

    @Override
    public void recordFailure(String key, Duration window) {
        String redisKey = PREFIX + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            // TTL solo al abrir la ventana; se repone si se perdió (p. ej. caída entre INCR y EXPIRE)
            // para que un contador nunca quede sin caducidad y bloquee la cuenta para siempre.
            Long ttl = redisTemplate.getExpire(redisKey);
            if ((count != null && count == 1) || ttl == null || ttl < 0) {
                redisTemplate.expire(redisKey, window);
            }
        } catch (RuntimeException exception) {
            log.warn("Could not record login failure for {}: {}", key, exception.getMessage());
        }
    }

    @Override
    public void clearFailures(String key) {
        try {
            redisTemplate.delete(PREFIX + key);
        } catch (RuntimeException exception) {
            log.warn("Could not clear login failures for {}: {}", key, exception.getMessage());
        }
    }

    @Override
    public void markTrusted(String key, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(TRUSTED_PREFIX + key, "1", ttl);
        } catch (RuntimeException exception) {
            log.warn("Could not mark trusted login for {}: {}", key, exception.getMessage());
        }
    }

    /** Si Redis falla, no es de confianza: tampoco hay contadores, así que no bloquea a nadie. */
    @Override
    public boolean isTrusted(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(TRUSTED_PREFIX + key));
        } catch (RuntimeException exception) {
            log.warn("Could not read trusted login for {}: {}", key, exception.getMessage());
            return false;
        }
    }
}
