package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.adapters.CacheAdapter;
import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.response.PokemonResponseApi;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PokemonCacheLoader {

    private final PokemonApiAdapter getPokemonsHandler;
    private final CacheAdapter cacheService;
    private final PokemonMapper pokemonMapper;

    public PokemonCacheLoader(PokemonApiAdapter getPokemonsHandler,
                              CacheAdapter cacheService,
                              PokemonMapper pokemonMapper) {
        this.getPokemonsHandler = getPokemonsHandler;
        this.cacheService = cacheService;
        this.pokemonMapper = pokemonMapper;
    }

    @PostConstruct
    public void init() {
        if (cacheService.isCached()) {
            log.info("Pokemon cache already present in Redis, skipping fetch");
            return;
        }

        log.info("Pokemon cache empty, fetching from PokeAPI...");
        PokemonResponseApi response = getPokemonsHandler.fetchAllPokemons();
        if (response == null) {
            throw new RuntimeException("Failed to load pokemons for cache");
        }
        response.getResults().forEach(pokemon -> pokemon.setId(getId(pokemon.getUrl())));
        cacheService.put(pokemonMapper.dtoToCacheDto(response.getResults()));
        log.info("Pokemon cache populated with {} entries", response.getResults().size());
    }

    private Integer getId(String url) {
        String[] parts = url.split("/");
        return Integer.parseInt(parts[6]);
    }
}
