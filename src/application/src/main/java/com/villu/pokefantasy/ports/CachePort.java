package com.villu.pokefantasy.ports;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.response.PokemonResponseApi;

import java.util.List;

public interface CachePort {

    void put( List<PokemonCacheDto> value);

    List<PokemonCacheDto>  getPokemon(String key);
}
