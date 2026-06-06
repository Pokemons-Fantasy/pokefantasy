package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Command;

public record SetLeagueMvpCommand(
        String leagueId,
        String username,
        String pokemonName
) implements Command {}
