package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.Command;

public record DeleteAvatarCommand(String username) implements Command {}
