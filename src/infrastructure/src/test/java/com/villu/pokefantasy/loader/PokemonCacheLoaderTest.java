package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.adapters.CacheAdapter;
import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.response.PokemonResponseApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PokemonCacheLoaderTest {

    @Mock private PokemonApiAdapter pokemonApiAdapter;
    @Mock private CacheAdapter cacheAdapter;
    @Mock private PokemonMapper pokemonMapper;

    private PokemonCacheLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PokemonCacheLoader(pokemonApiAdapter, cacheAdapter, pokemonMapper);
    }

    @Test
    void init_cacheAlreadyPopulated_skipsFetch() {
        when(cacheAdapter.isCached()).thenReturn(true);

        loader.init();

        verify(pokemonApiAdapter, never()).fetchAllPokemons();
        verify(cacheAdapter, never()).put(any());
        verify(pokemonMapper, never()).dtoToCacheDto(any());
    }

    @Test
    void init_cacheEmpty_fetchesFromApiAndPopulates() {
        when(cacheAdapter.isCached()).thenReturn(false);

        ResultPokemonDto pikachu = new ResultPokemonDto();
        pikachu.setName("pikachu");
        pikachu.setUrl("https://pokeapi.co/api/v2/pokemon/25/");

        PokemonResponseApi response = new PokemonResponseApi();
        response.setResults(List.of(pikachu));

        when(pokemonApiAdapter.fetchAllPokemons()).thenReturn(response);
        when(pokemonMapper.dtoToCacheDto(response.getResults()))
                .thenReturn(List.of(new PokemonCacheDto(pikachu.getUrl(), pikachu.getName(), 25)));

        loader.init();

        verify(pokemonApiAdapter).fetchAllPokemons();
        verify(pokemonMapper).dtoToCacheDto(response.getResults());
        verify(cacheAdapter).put(any());
    }

    @Test
    void init_cacheEmptyAndApiReturnsNull_throwsRuntimeException() {
        when(cacheAdapter.isCached()).thenReturn(false);
        when(pokemonApiAdapter.fetchAllPokemons()).thenReturn(null);

        assertThatThrownBy(() -> loader.init())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load pokemons");

        verify(cacheAdapter, never()).put(any());
    }
}
