package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record PromoteMemberToAdminCommand(String leagueId, String targetUsername, String requestingUsername) implements Command {
}
