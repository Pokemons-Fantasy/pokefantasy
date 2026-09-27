package com.villu.pokefantasy.commands.users.login;

import com.villu.pokefantasy.mediator.Command;

/** {@code clientIp} puede ser null (p. ej. en tests); entonces solo se limita por usuario. */
public record LoginUserCommand(String username, String password, String clientIp) implements Command {

}

