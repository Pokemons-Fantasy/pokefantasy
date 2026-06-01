package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.mediator.Command;

public record GenerateInviteLinkCommand(String leagueId, String requestingUsername) implements Command {}
