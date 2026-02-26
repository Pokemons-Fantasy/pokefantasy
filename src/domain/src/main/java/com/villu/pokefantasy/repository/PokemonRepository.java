package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.PokemonEntity;

import java.util.List;

public interface PokemonRepository{

    void addPokemons(List<PokemonEntity> pokemonEntity);
}
