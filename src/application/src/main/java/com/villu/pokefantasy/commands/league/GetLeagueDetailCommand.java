package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record GetLeagueDetailCommand(String leagueId, String requestingUsername) implements Command {
}
