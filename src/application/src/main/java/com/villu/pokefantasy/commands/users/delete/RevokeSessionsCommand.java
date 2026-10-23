package com.villu.pokefantasy.commands.users.delete;

import com.villu.pokefantasy.mediator.Command;

public record RevokeSessionsCommand(String username) implements Command {}
