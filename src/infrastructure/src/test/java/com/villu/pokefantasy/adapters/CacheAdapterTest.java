package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheAdapterTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ObjectMapper redisObjectMapper;
    @Mock private ValueOperations<String, Object> valueOperations;

    private CacheAdapter cacheAdapter;

    private static final String CACHE_KEY = "all_pokemons";

    @BeforeEach
    void setUp() {
        cacheAdapter = new CacheAdapter();
        ReflectionTestUtils.setField(cacheAdapter, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(cacheAdapter, "redisObjectMapper", redisObjectMapper);
    }

    @Test
    void put_serializesAndStoresUnderCacheKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<PokemonCacheDto> value = List.of(new PokemonCacheDto("url", "pikachu", 25));
        when(redisObjectMapper.writeValueAsString(value)).thenReturn("[serialized]");

        cacheAdapter.put(value);

        verify(valueOperations).set(CACHE_KEY, "[serialized]");
    }

    @Test
    void isCached_keyPresent_returnsTrue() {
        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(true);

        assertThat(cacheAdapter.isCached()).isTrue();
    }

    @Test
    void isCached_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(false);

        assertThat(cacheAdapter.isCached()).isFalse();
    }

    @Test
    void isCached_nullFromRedis_returnsFalse() {
        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(null);

        assertThat(cacheAdapter.isCached()).isFalse();
    }

    @Test
    void getPokemon_cacheEmpty_returnsEmptyList() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        assertThat(cacheAdapter.getPokemon(CACHE_KEY)).isEmpty();
    }

    @Test
    void getPokemon_cacheHit_deserializesList() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn("[{\"name\":\"pikachu\"}]");
        TypeFactory typeFactory = TypeFactory.createDefaultInstance();
        when(redisObjectMapper.getTypeFactory()).thenReturn(typeFactory);
        JavaType javaType = typeFactory.constructCollectionType(List.class, PokemonCacheDto.class);
        List<PokemonCacheDto> expected = List.of(new PokemonCacheDto("url", "pikachu", 25));
        when(redisObjectMapper.readValue(eq("[{\"name\":\"pikachu\"}]"), any(JavaType.class))).thenReturn(expected);

        List<PokemonCacheDto> result = cacheAdapter.getPokemon(CACHE_KEY);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getPokemon_deserializationThrows_returnsEmptyList() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn("not-valid-json");
        when(redisObjectMapper.getTypeFactory()).thenThrow(new RuntimeException("boom"));

        assertThat(cacheAdapter.getPokemon(CACHE_KEY)).isEmpty();
    }
}
