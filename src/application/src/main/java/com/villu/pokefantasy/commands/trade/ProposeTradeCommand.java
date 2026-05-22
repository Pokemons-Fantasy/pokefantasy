package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

public record ProposeTradeCommand(
        String leagueId,
        String proposer,
        String responder,
        String proposerPokemonName,
        String responderPokemonName,
        int coinsOffered
) implements Command {}
