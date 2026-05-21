package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.mediator.Command;

public record StealPokemonCommand(
        String leagueId,
        String stealer,
        String targetPokemonName
) implements Command {}
