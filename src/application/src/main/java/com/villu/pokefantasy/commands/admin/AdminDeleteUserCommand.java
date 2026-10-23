package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.mediator.Command;

public record AdminDeleteUserCommand(String adminUsername, String targetUsername) implements Command {}
