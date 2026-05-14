package com.villu.pokefantasy.adapters;
import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.ports.CachePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.List;


@Component
@Slf4j
public class CacheAdapter implements CachePort {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ObjectMapper redisObjectMapper;

    private static final String CACHE_KEY = "all_pokemons";

    @Override
    public void put( List<PokemonCacheDto> value) {
        redisTemplate.opsForValue().set(CACHE_KEY, redisObjectMapper.writeValueAsString(value));
    }

    @Override
    public List<PokemonCacheDto> getPokemon(String key) {
        try {
            Object data = redisTemplate.opsForValue().get(CACHE_KEY);
            if (data == null) {
                log.warn("Caché vacío para clave: {}", key);
                return List.of();
            }
            JavaType javaType = redisObjectMapper.getTypeFactory()
                    .constructCollectionType(List.class, PokemonCacheDto.class);
            return redisObjectMapper.readValue(data.toString(), javaType);
        } catch (Exception e) {
            log.error("Error deserializando datos de caché", e);
            return List.of();
        }
    }

}
