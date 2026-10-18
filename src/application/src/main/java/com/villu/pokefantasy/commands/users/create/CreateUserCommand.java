package com.villu.pokefantasy.commands.users.create;

import com.villu.pokefantasy.mediator.Command;

/** {@code clientIp} puede ser null (p. ej. en tests); entonces no se limita el registro por IP. */
public record CreateUserCommand(String username, String password, String clientIp) implements Command {

}

