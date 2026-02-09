package com.villu.pokefantasy.mapper;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface PokemonMapper {

    PokemonsResponse dtoToResponse(Pokemons pokemons);
}
