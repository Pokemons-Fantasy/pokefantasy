package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.mediator.Command;

public record GetBenchCommand(String leagueId, String requestingUsername) implements Command {}
