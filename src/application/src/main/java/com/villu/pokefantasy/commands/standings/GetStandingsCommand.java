package com.villu.pokefantasy.commands.standings;

import com.villu.pokefantasy.mediator.Command;

public record GetStandingsCommand(String leagueId) implements Command {
}
