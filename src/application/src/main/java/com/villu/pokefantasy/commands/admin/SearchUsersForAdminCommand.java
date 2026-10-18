package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.mediator.Command;

public record SearchUsersForAdminCommand(String q) implements Command {}
