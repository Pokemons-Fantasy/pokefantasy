package com.villu.pokefantasy.security;

import com.villu.pokefantasy.ports.RefreshTokenPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Refresh tokens en Redis: {@code refresh:<sha256(token)>} → username, con TTL deslizante.
 * Se guarda el hash, no el token: quien lea Redis no puede suplantar a nadie.
 *
 * <p>A diferencia del límite de login, aquí los fallos de Redis cierran (fail-closed): si no se puede
 * comprobar el token, no hay refresco y el usuario tendrá que volver a entrar.
 */
@Component
@Slf4j
public class RefreshTokenRedisAdapter implements RefreshTokenPort {

    static final String PREFIX = "refresh:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenRedisAdapter(StringRedisTemplate redisTemplate,
                                    @Value("${jwt.refresh-expiration-days:30}") long ttlDays) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(ttlDays);
    }

    @Override
    public String issue(String username) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redisTemplate.opsForValue().set(key(token), username, ttl);
            return token;
        } catch (RuntimeException exception) {
            log.warn("Could not store refresh token for {}: {}", username, exception.getMessage());
            return null;
        }
    }

    @Override
    public Optional<String> resolve(String token) {
        try {
            String key = key(token);
            String username = redisTemplate.opsForValue().get(key);
            if (username != null) {
                redisTemplate.expire(key, ttl);
            }
            return Optional.ofNullable(username);
        } catch (RuntimeException exception) {
            log.warn("Could not resolve refresh token: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void revoke(String token) {
        try {
            redisTemplate.delete(key(token));
        } catch (RuntimeException exception) {
            log.warn("Could not revoke refresh token: {}", exception.getMessage());
        }
    }

    @Override
    public Duration ttl() {
        return ttl;
    }

    static String key(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
