package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.mediator.Command;

public record NominatePokemonCommand(String username, String pokemonName) implements Command {}
