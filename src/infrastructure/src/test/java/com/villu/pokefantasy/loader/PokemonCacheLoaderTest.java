package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.adapters.CacheAdapter;
import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import com.villu.pokefantasy.dto.TypeMember;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.response.PokemonResponseApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PokemonCacheLoaderTest {

    @Mock private PokemonApiAdapter pokemonApiAdapter;
    @Mock private CacheAdapter cacheAdapter;
    @Mock private PokemonMapper pokemonMapper;

    private PokemonCacheLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PokemonCacheLoader(pokemonApiAdapter, cacheAdapter, pokemonMapper);
        // Bulbasaur es planta (slot 1) y veneno (slot 2); el resto de tipos, vacíos
        when(pokemonApiAdapter.fetchTypeMembers(anyString())).thenReturn(List.of());
        when(pokemonApiAdapter.fetchTypeMembers("grass")).thenReturn(List.of(new TypeMember("bulbasaur", 1)));
        when(pokemonApiAdapter.fetchTypeMembers("poison")).thenReturn(List.of(new TypeMember("bulbasaur", 2)));
    }

    private static PokemonResponseApi apiResponse() {
        ResultPokemonDto bulbasaur = new ResultPokemonDto();
        bulbasaur.setName("bulbasaur");
        bulbasaur.setUrl("https://pokeapi.co/api/v2/pokemon/1/");
        PokemonResponseApi response = new PokemonResponseApi();
        response.setResults(List.of(bulbasaur));
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<PokemonCacheDto> captureStored() {
        ArgumentCaptor<List<PokemonCacheDto>> stored = ArgumentCaptor.forClass(List.class);
        verify(cacheAdapter).put(stored.capture());
        return stored.getValue();
    }

    @Test
    void init_cacheCompleteWithTypes_skipsFetch() {
        when(cacheAdapter.isCached()).thenReturn(true);
        when(cacheAdapter.getPokemon(any())).thenReturn(List.of(new PokemonCacheDto("u", "bulbasaur", 1, List.of("grass"))));

        loader.init();

        verify(pokemonApiAdapter, never()).fetchAllPokemons();
        verify(pokemonApiAdapter, never()).fetchTypeMembers(anyString());
        verify(cacheAdapter, never()).put(any());
    }

    @Test
    void init_cacheEmpty_fetchesListAndTypesInSlotOrder() {
        when(cacheAdapter.isCached()).thenReturn(false);
        PokemonResponseApi response = apiResponse();
        when(pokemonApiAdapter.fetchAllPokemons()).thenReturn(response);
        when(pokemonMapper.dtoToCacheDto(response.getResults())).thenAnswer(inv -> response.getResults().stream()
                .map(r -> new PokemonCacheDto(r.getUrl(), r.getName(), r.getId(), r.getTypes())).toList());

        loader.init();

        verify(pokemonApiAdapter, times(PokemonCacheLoader.TYPES.size())).fetchTypeMembers(anyString());
        assertThat(captureStored()).containsExactly(
                new PokemonCacheDto("https://pokeapi.co/api/v2/pokemon/1/", "bulbasaur", 1, List.of("grass", "poison")));
    }

    @Test
    void init_cacheEmptyAndTypesFail_storesListWithoutTypesSoNominatingWorks() {
        when(cacheAdapter.isCached()).thenReturn(false);
        PokemonResponseApi response = apiResponse();
        when(pokemonApiAdapter.fetchAllPokemons()).thenReturn(response);
        when(pokemonApiAdapter.fetchTypeMembers("normal")).thenThrow(new ResourceAccessException("timeout"));
        when(pokemonMapper.dtoToCacheDto(response.getResults())).thenAnswer(inv -> response.getResults().stream()
                .map(r -> new PokemonCacheDto(r.getUrl(), r.getName(), r.getId(), r.getTypes())).toList());

        loader.init();

        assertThat(captureStored()).containsExactly(
                new PokemonCacheDto("https://pokeapi.co/api/v2/pokemon/1/", "bulbasaur", 1, null));
    }

    @Test
    void init_cacheWithoutTypes_fillsOnlyTheTypes() {
        when(cacheAdapter.isCached()).thenReturn(true);
        when(cacheAdapter.getPokemon(any())).thenReturn(List.of(new PokemonCacheDto("u", "bulbasaur", 1, null)));

        loader.init();

        verify(pokemonApiAdapter, never()).fetchAllPokemons();
        assertThat(captureStored()).containsExactly(new PokemonCacheDto("u", "bulbasaur", 1, List.of("grass", "poison")));
    }

    @Test
    void ensureLoaded_cacheWithoutTypesAndTypesFail_keepsCacheAndDoesNotPropagate() {
        when(cacheAdapter.isCached()).thenReturn(true);
        when(cacheAdapter.getPokemon(any())).thenReturn(List.of(new PokemonCacheDto("u", "bulbasaur", 1, null)));
        when(pokemonApiAdapter.fetchTypeMembers("normal")).thenThrow(new ResourceAccessException("timeout"));

        assertThatCode(() -> loader.ensureLoaded()).doesNotThrowAnyException();
        verify(cacheAdapter, never()).put(any());
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

    @Test
    void ensureLoaded_pokeApiDown_doesNotPropagateSoStartupContinues() {
        when(cacheAdapter.isCached()).thenReturn(false);
        when(pokemonApiAdapter.fetchAllPokemons()).thenThrow(new ResourceAccessException("timeout"));

        assertThatCode(() -> loader.ensureLoaded()).doesNotThrowAnyException();
        verify(cacheAdapter, never()).put(any());
    }

    @Test
    void ensureLoaded_redisDown_doesNotPropagate() {
        when(cacheAdapter.isCached()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> loader.ensureLoaded()).doesNotThrowAnyException();
    }
}
