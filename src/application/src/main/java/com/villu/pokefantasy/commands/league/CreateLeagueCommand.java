package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record CreateLeagueCommand(String name, String creatorUsername) implements Command {
}
