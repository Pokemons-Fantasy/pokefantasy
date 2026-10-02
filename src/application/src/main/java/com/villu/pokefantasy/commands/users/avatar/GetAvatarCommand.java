package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.Command;

public record GetAvatarCommand(String username, String requestingUsername) implements Command {}
