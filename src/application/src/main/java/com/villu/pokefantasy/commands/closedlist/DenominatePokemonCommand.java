package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.mediator.Command;

public record DenominatePokemonCommand(String username, String pokemonName) implements Command {
}
