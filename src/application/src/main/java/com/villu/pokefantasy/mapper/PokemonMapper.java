package com.villu.pokefantasy.mapper;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommandResponse;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import com.villu.pokefantasy.repository.entity.PokemonEntity;
import com.villu.pokefantasy.response.PokemonResponseApi;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PokemonMapper {

    GetPokemonCommandResponse dtoToResponse(Pokemons pokemons);

    List<PokemonCacheDto> dtoToCacheDto(List<ResultPokemonDto> pokemons);

    PokemonsResponse commandToResponse(GetPokemonCommandResponse pokemon);

    ResultPokemonDto fromCacheDtoToResponse(PokemonCacheDto pokemonCacheDto);

    List<PokemonEntity> fromResultToEntity(List<ResultPokemonDto> resultPokemonDto);

    List<ResultPokemonDto> fromEntityToResult(List<PokemonEntity> pokemonEntity);

}
