package com.villu.pokefantasy.ports;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.response.PokemonResponseApi;

public interface PokemonApiPort {
    PokemonResponseApi fetchAllPokemons();
    Pokemons fetchPokemonById(String url,String name) throws Exception;
    Pokemons fetchPokemonData(String url);
}
