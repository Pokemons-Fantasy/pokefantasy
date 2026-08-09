package com.villu.pokefantasy.commands.activity;

import com.villu.pokefantasy.mediator.Command;

public record GetActivityFeedCommand(
        String leagueId,
        String username,
        int page,
        int size,
        String requestingUsername
) implements Command {}
