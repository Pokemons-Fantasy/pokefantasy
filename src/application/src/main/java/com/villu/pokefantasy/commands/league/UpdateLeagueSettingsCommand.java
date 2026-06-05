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
        String seasonStartDate,
        Integer maxTeamSize,
        Integer tierPctS,
        Integer tierPctA,
        Integer tierPctB,
        Integer tierPctC,
        Integer tierPctD,
        Integer turnTimerSeconds,
        Integer stealWindowCloseDay,
        String stealWindowCloseTime,
        Integer swapWindowCloseDay,
        String swapWindowCloseTime,
        String requestingUsername
) implements Command {}
