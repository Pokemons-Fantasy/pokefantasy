package com.villu.pokefantasy.ports;

import com.villu.pokefantasy.response.PokemonResponseApi;

public interface CachePort {

    void put(String key, Object value);

    PokemonResponseApi getPokemon(String key);
}
