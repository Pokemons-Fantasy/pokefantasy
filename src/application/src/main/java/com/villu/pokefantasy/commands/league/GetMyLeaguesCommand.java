package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record GetMyLeaguesCommand(String username) implements Command {
}
