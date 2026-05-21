package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.mediator.Command;

public record SetStealPriceCommand(
        String leagueId,
        String username,
        String pokemonName,
        int newPrice
) implements Command {}
