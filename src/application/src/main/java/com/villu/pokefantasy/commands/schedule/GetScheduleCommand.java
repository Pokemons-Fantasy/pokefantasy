package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

public record GetScheduleCommand(String leagueId, String requestingUsername) implements Command {
}
