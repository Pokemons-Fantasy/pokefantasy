package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.mediator.Command;

public record SwapWithBenchCommand(
        String leagueId,
        String username,
        String pokemonToGive,
        String pokemonToTake
) implements Command {}
