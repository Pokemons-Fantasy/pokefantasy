package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record CancelTradeCommand(
        String leagueId,
        String tradeId,
        String requestingUser
) implements Command {}
