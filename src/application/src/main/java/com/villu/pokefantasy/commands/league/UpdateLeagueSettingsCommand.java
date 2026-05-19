package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record UpdateLeagueSettingsCommand(
        String leagueId,
        Integer coinsPerWin,
        Integer coinsPerLoss,
        String requestingUsername
) implements Command {}
