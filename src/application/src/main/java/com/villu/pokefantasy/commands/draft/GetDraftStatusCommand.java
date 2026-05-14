package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

public record GetDraftStatusCommand(String leagueId) implements Command {}
