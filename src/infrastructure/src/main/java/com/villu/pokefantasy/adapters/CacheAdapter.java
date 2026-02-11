package com.villu.pokefantasy.adapters;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.response.PokemonResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class CacheAdapter implements CachePort {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper redisObjectMapper;

    @Override
    public void put(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public PokemonResponseApi getPokemon(String key) {
        Object pokemons = redisTemplate.opsForValue().get(key);
        return redisObjectMapper.convertValue(pokemons, PokemonResponseApi.class);
    }
}
