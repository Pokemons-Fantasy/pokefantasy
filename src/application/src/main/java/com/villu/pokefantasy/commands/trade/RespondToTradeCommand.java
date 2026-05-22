package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record RespondToTradeCommand(
        String leagueId,
        String tradeId,
        String respondingUser,
        boolean accept
) implements Command {}
