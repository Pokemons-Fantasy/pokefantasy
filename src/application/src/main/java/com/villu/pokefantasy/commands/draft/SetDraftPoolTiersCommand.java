package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.Command;

import java.util.List;

public record SetDraftPoolTiersCommand(String leagueId, String requestingUsername, List<String> entryIds,
                                       Tier tier) implements Command {}
