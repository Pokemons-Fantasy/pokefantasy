package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

public record ResetDraftPoolTiersCommand(String leagueId, String requestingUsername) implements Command {}
