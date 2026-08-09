package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.mediator.Command;

public record GetClosedListCommand(String leagueId, String requestingUsername) implements Command {}
