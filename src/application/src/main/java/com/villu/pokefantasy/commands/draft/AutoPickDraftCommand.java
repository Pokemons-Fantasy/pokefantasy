package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

public record AutoPickDraftCommand(String leagueId) implements Command {}
