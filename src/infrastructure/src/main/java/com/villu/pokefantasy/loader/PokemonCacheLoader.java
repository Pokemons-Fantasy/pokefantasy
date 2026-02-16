package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.adapters.CacheAdapter;
import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import com.villu.pokefantasy.mapper.PokemonMapper;
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

    @Autowired
    private PokemonMapper pokemonMapper;

    @PostConstruct
    public void init()
    {
        PokemonResponseApi response = getPokemonsHandler.fetchAllPokemons();
        if (response == null){
            throw new RuntimeException("Failed to load pokemons for cache");
        }
        response.getResults().forEach(pokemon -> pokemon.setId(getId(pokemon.getUrl())));
        cacheService.put(pokemonMapper.dtoToCacheDto(response.getResults()));
    }

    private Integer getId(String url) {
        String[] parts = url.split("/");
        return Integer.parseInt(parts[6]);
    }
}
