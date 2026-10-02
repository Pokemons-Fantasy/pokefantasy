package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.mediator.Command;

public record UnregisterPushTokenCommand(String username, String token) implements Command {}
