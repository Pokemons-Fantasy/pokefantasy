package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record GetTradesCommand(
        String leagueId,
        String username
) implements Command {}
