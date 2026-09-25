package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.adapters.CacheAdapter;
import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.response.PokemonResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantiene en Redis la lista de todos los Pokémon de PokeAPI (la usan nominar y el buscador).
 *
 * <p>No bloquea el arranque: se ejecuta en segundo plano al arrancar y después cada minuto. Si la caché
 * ya está, no hace nada (una consulta a Redis); si falta, porque PokeAPI falló o porque Redis se vació,
 * vuelve a intentarlo. Mientras tanto la app funciona y solo nominar responde "aún cargando".
 */
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

    @Scheduled(initialDelay = 0, fixedDelayString = "${pokemon-cache.check-interval-ms:60000}")
    public void ensureLoaded() {
        try {
            init();
        } catch (RuntimeException e) {
            log.warn("Could not load Pokémon cache, will retry: {}", e.getMessage());
        }
    }

    void init() {
        if (cacheService.isCached()) {
            log.debug("Pokemon cache already present in Redis, skipping fetch");
            return;
        }

        log.info("Pokemon cache empty, fetching from PokeAPI...");
        PokemonResponseApi response = getPokemonsHandler.fetchAllPokemons();
        if (response == null || response.getResults() == null) {
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
