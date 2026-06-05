package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.mediator.Command;

public record ReleasePokemonCommand(
        String leagueId,
        String username,
        String pokemonName
) implements Command {}
