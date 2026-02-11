package com.villu.pokefantasy.commands.getPokemon;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.response.PokemonResponseApi;
import com.villu.pokefantasy.response.PokemonsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GetPokemonRest {

    @Autowired
    private CachePort cacheService;

    @Autowired
    private PokemonApiPort getPokemonApi;

    @Autowired
    private PokemonMapper pokemonMapper;

    private static final String CACHE_KEY = "all_pokemons";

    public PokemonsResponse getPokemonById(int id) throws Exception {
        // Lógica para obtener el Pokémon por ID
        return getPokemonCacheById(id);
    }

    private PokemonsResponse getPokemonCacheById(int id) throws Exception {
        PokemonResponseApi pokemonsResponse = cacheService.getPokemon(CACHE_KEY);

        return pokemonMapper.dtoToResponse(getDataFromPokemon(pokemonsResponse.getResults().get(id-1)));
    }

    private Pokemons getDataFromPokemon(ResultPokemonDto getPokemonsResponse) throws Exception {
        return getPokemonApi.fetchPokemonById(getPokemonsResponse.getUrl(), getPokemonsResponse.getName());
    }
}
