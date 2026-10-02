package com.villu.pokefantasy.loader;

import com.villu.pokefantasy.adapters.CacheAdapter;
import com.villu.pokefantasy.adapters.PokemonApiAdapter;
import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.TypeMember;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.response.PokemonResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mantiene en Redis la lista de todos los Pokémon de PokeAPI con sus tipos (la usan nominar y el Pool).
 *
 * <p>No bloquea el arranque: se ejecuta en segundo plano al arrancar y después cada minuto.
 * <ul>
 *   <li>Sin caché (PokeAPI falló o Redis se vació): baja la lista y los tipos. Si fallan los tipos, guarda
 *       la lista sin ellos para que nominar funcione.</li>
 *   <li>Caché sin tipos (anterior a guardarlos o de una carga en la que fallaron): rellena solo los tipos.</li>
 *   <li>Caché completa: no hace nada (una consulta a Redis).</li>
 * </ul>
 * Mientras tanto la app funciona y solo nominar responde "aún cargando".
 */
@Component
@Slf4j
public class PokemonCacheLoader {

    /** Los 18 tipos jugables; PokeAPI tiene también "unknown", "shadow" y "stellar", sin Pokémon normales. */
    static final List<String> TYPES = List.of(
            "normal", "fighting", "flying", "poison", "ground", "rock", "bug", "ghost", "steel",
            "fire", "water", "grass", "electric", "psychic", "ice", "dragon", "dark", "fairy");

    private static final String CACHE_KEY = "pokemons";

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
            List<PokemonCacheDto> cached = cacheService.getPokemon(CACHE_KEY);
            if (cached.isEmpty() || cached.stream().anyMatch(p -> p.getTypes() != null)) {
                log.debug("Pokemon cache already present in Redis, skipping fetch");
                return;
            }
            log.info("Pokemon cache without types, fetching types from PokeAPI...");
            Map<String, List<String>> types = fetchTypes();
            cacheService.put(cached.stream().map(p -> p.withTypes(types.get(p.getName()))).toList());
            log.info("Pokemon cache types filled for {} entries", types.size());
            return;
        }

        log.info("Pokemon cache empty, fetching from PokeAPI...");
        PokemonResponseApi response = getPokemonsHandler.fetchAllPokemons();
        if (response == null || response.getResults() == null) {
            throw new RuntimeException("Failed to load pokemons for cache");
        }
        Map<String, List<String>> types = fetchTypesOrEmpty();
        response.getResults().forEach(pokemon -> {
            pokemon.setId(getId(pokemon.getUrl()));
            pokemon.setTypes(types.get(pokemon.getName()));
        });
        cacheService.put(pokemonMapper.dtoToCacheDto(response.getResults()));
        log.info("Pokemon cache populated with {} entries", response.getResults().size());
    }

    /** Tipos de cada Pokémon por nombre, en orden de slot: 18 llamadas, una por tipo. */
    private Map<String, List<String>> fetchTypes() {
        Map<String, TreeMap<Integer, String>> bySlot = new HashMap<>();
        for (String type : TYPES) {
            for (TypeMember member : getPokemonsHandler.fetchTypeMembers(type)) {
                bySlot.computeIfAbsent(member.pokemonName(), name -> new TreeMap<>()).put(member.slot(), type);
            }
        }
        Map<String, List<String>> types = new HashMap<>();
        bySlot.forEach((name, slots) -> types.put(name, List.copyOf(slots.values())));
        return types;
    }

    /** Si fallan los tipos, la lista se guarda igual: la próxima comprobación los rellena. */
    private Map<String, List<String>> fetchTypesOrEmpty() {
        try {
            return fetchTypes();
        } catch (RuntimeException e) {
            log.warn("Could not load Pokémon types, caching the list without them: {}", e.getMessage());
            return Map.of();
        }
    }

    private Integer getId(String url) {
        String[] parts = url.split("/");
        return Integer.parseInt(parts[6]);
    }
}
