package com.villu.pokefantasy.mapper;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PokemonMapper {

    List<PokemonCacheDto> dtoToCacheDto(List<ResultPokemonDto> pokemons);

}
