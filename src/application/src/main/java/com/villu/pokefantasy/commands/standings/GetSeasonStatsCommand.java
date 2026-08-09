package com.villu.pokefantasy.commands.standings;

import com.villu.pokefantasy.mediator.Command;

public record GetSeasonStatsCommand(String leagueId, String requestingUsername) implements Command {}
