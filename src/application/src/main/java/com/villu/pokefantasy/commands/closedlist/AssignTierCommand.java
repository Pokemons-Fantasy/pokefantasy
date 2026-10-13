package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.Command;

import java.util.List;

public record AssignTierCommand(List<String> entryIds, Tier tier, String leagueId, String requestingUsername) implements Command {}
