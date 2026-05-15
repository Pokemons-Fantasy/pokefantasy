package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

public record CancelDraftCommand(String leagueId, String requestingUsername) implements Command {}
