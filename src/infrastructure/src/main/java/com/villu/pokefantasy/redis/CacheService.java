package com.villu.pokefantasy.redis;

import com.villu.pokefantasy.initializePkmn.GetPokemonsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Service
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper redisObjectMapper;

    public void put(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public GetPokemonsResponse get(String key) {
        Object raw = Objects.requireNonNull(redisTemplate.opsForValue().get(key));
        if (raw instanceof GetPokemonsResponse typed) {
            return typed;
        }
        // Cuando se deserializa como Map (p.ej. LinkedHashMap), lo convertimos al tipo esperado.
        return redisObjectMapper.convertValue(raw, GetPokemonsResponse.class);
    }

    public <T> T get(String key, Class<T> type) {
        Object raw = Objects.requireNonNull(redisTemplate.opsForValue().get(key));
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        return redisObjectMapper.convertValue(raw, type);
    }
}
