package com.villu.pokefantasy.commands.activity;

import com.villu.pokefantasy.mediator.Command;

public record GetActivityFeedCommand(
        String leagueId,
        int page,
        int size
) implements Command {}
