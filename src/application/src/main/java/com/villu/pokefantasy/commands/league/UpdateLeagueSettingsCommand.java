package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record UpdateLeagueSettingsCommand(
        String leagueId,
        Integer coinsPerWin,
        Integer coinsPerLoss,
        Integer priceTierS,
        Integer priceTierA,
        Integer priceTierB,
        Integer priceTierC,
        Integer priceTierD,
        String requestingUsername
) implements Command {}
