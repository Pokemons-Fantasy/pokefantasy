package com.villu.pokefantasy.commands.users.login;

import com.villu.pokefantasy.mediator.Command;

public record LoginUserCommand(String username, String password) implements Command {

}

