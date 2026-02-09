package com.villu.pokefantasy.commands.getPokemon;

import com.villu.pokefantasy.commands.getDataPokemon.GetPokemonDataCommand;
import com.villu.pokefantasy.commands.getDataPokemon.GetPokemonDataHandler;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import com.villu.pokefantasy.initializePkmn.GetPokemonsResponse;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.redis.CacheService;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetPokemonRest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private GetPokemonDataHandler getPokemonDataHandler;

    @Autowired
    private PokemonMapper pokemonMapper;

    private static final String CACHE_KEY = "all_pokemons";

    public PokemonsResponse getPokemonById(int id) throws Exception {
        // Lógica para obtener el Pokémon por ID
        return getPokemonCacheById(id);
    }

    private PokemonsResponse getPokemonCacheById(int id) throws Exception {
        GetPokemonsResponse pokemonsResponse = cacheService.get(CACHE_KEY);
        return pokemonMapper.dtoToResponse(getDataFromPokemon(pokemonsResponse.getResults().get(id-1)));
    }

    private Pokemons getDataFromPokemon(ResultPokemonDto getPokemonsResponse) throws Exception {

        GetPokemonDataCommand command = GetPokemonDataCommand.builder().
                name(getPokemonsResponse.getName()).url(getPokemonsResponse.getUrl()).build();

        return getPokemonDataHandler.handle(command);
    }
}
