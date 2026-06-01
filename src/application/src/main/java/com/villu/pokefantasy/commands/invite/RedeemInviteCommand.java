package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.mediator.Command;

public record RedeemInviteCommand(String token, String username) implements Command {}
