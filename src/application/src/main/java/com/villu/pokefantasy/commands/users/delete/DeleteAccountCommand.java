package com.villu.pokefantasy.commands.users.delete;

import com.villu.pokefantasy.mediator.Command;

public record DeleteAccountCommand(String username, String password) implements Command {}
