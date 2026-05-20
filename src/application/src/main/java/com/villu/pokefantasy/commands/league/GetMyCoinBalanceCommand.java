package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record GetMyCoinBalanceCommand(String leagueId, String requestingUsername) implements Command {}
