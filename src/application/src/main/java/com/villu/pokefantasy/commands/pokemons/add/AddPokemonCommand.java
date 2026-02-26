package com.villu.pokefantasy.commands.pokemons.add;

import com.villu.pokefantasy.mediator.Command;

import java.util.List;

public record AddPokemonCommand(List<String> pokemonsNames) implements Command {
}
