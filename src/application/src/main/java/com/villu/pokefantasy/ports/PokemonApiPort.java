package com.villu.pokefantasy.ports;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.TypeMember;
import com.villu.pokefantasy.response.PokemonResponseApi;

import java.util.List;

public interface PokemonApiPort {
    PokemonResponseApi fetchAllPokemons();
    Pokemons fetchPokemonById(String url,String name) throws Exception;
    Pokemons fetchPokemonData(String url);
    /** Pokémon de un tipo ({@code GET /type/{type}}), con el slot que ocupa ese tipo en cada uno. */
    List<TypeMember> fetchTypeMembers(String type);
}
