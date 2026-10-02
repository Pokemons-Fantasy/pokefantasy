package com.villu.pokefantasy.dto;

/**
 * Un Pokémon de un tipo según PokeAPI ({@code GET /type/{tipo}}).
 *
 * @param slot 1 si es su tipo principal, 2 si es el secundario
 */
public record TypeMember(String pokemonName, int slot) {}
