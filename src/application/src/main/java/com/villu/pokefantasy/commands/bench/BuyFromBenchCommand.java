package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.mediator.Command;

public record BuyFromBenchCommand(
        String leagueId,
        String username,
        String pokemonName
) implements Command {}
