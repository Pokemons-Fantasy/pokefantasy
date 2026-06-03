package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.mediator.Command;

public record RegisterPushTokenCommand(String username, String token) implements Command {}
