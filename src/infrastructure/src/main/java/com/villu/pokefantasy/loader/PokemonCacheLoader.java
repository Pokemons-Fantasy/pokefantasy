package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.adapters.CacheAdapter;
import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import com.villu.pokefantasy.response.PokemonResponseApi;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PokemonCacheLoader {

    @Autowired
    private PokemonApiAdapter getPokemonsHandler;

    @Autowired
    private CacheAdapter cacheService;

    private static final String CACHE_KEY = "all_pokemons";

    @PostConstruct
    public void init()
    {
        PokemonResponseApi response = getPokemonsHandler.fetchAllPokemons();
        if (response == null){
            throw new RuntimeException("Failed to load pokemons for cache");
        }
        cacheService.put(CACHE_KEY, response);
    }
}
