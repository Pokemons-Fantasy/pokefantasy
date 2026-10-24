package com.villu.pokefantasy.commands.users.showdown;

import com.villu.pokefantasy.mediator.Command;

public record UnlinkShowdownCommand(String username) implements Command {}
