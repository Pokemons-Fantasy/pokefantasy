package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.Command;

public record AssignTierCommand(String entryId, Tier tier, String leagueId, String requestingUsername) implements Command {}
