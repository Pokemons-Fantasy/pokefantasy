package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.initializePkmn.GetPokemonsHandler;
import com.villu.pokefantasy.initializePkmn.GetPokemonsResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.villu.pokefantasy.redis.CacheService;

@Component
public class PokemonCacheLoader {

    @Autowired
    private GetPokemonsHandler getPokemonsHandler;

    @Autowired
    private CacheService cacheService;

    private static final String CACHE_KEY = "all_pokemons";

    @PostConstruct
    public void init()
    {
        GetPokemonsResponse response = getPokemonsHandler.handle();
        if (response == null){
            throw new RuntimeException("Failed to load pokemons for cache");
        }
        cacheService.put(CACHE_KEY, response);
    }
}
