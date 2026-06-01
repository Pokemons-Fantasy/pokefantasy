package com.villu.pokefantasy.commands.users.search;

import com.villu.pokefantasy.mediator.Command;

public record SearchUsersCommand(String prefix, String leagueId) implements Command {}
