package com.villu.pokefantasy.commands.users.logout;

import com.villu.pokefantasy.mediator.Command;

public record LogoutUserCommand(String refreshToken) implements Command {}
