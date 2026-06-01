package com.villu.pokefantasy.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class InviteRepositoryImpl implements InviteRepository {
    private static final String PREFIX = "invite:";
    private final StringRedisTemplate redisTemplate;

    public InviteRepositoryImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String token, String leagueId, long ttlSeconds) {
        redisTemplate.opsForValue().set(PREFIX + token, leagueId, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String findLeagueId(String token) {
        return redisTemplate.opsForValue().get(PREFIX + token);
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(PREFIX + token);
    }
}
