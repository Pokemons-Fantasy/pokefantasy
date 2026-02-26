package com.villu.pokefantasy.commands.users.create;

import com.villu.pokefantasy.mediator.Command;

public record CreateUserCommand(String username, String password) implements Command {

}

