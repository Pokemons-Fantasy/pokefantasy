package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.mediator.Command;

public record RenameLeagueCommand(String adminUsername, String leagueId, String name) implements Command {}
