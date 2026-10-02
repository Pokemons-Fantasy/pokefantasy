package com.villu.pokefantasy.commands.users.me;

import com.villu.pokefantasy.mediator.Command;

public record GetCurrentUserCommand(String username) implements Command {}
