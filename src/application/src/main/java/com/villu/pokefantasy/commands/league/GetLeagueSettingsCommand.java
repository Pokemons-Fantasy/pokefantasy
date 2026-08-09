package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record GetLeagueSettingsCommand(String leagueId, String requestingUsername) implements Command {}
