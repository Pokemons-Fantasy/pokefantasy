package com.villu.pokefantasy.commands.pokemons.get;

import com.villu.pokefantasy.mediator.Command;

public record GetPokemonCommand(int id) implements Command {
}
