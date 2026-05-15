package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record RemoveMemberFromLeagueCommand(String leagueId, String targetUsername, String requestingUsername) implements Command {
}
