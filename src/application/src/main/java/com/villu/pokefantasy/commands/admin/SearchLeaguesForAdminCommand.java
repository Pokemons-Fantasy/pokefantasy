package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.mediator.Command;

public record SearchLeaguesForAdminCommand(String q) implements Command {}
