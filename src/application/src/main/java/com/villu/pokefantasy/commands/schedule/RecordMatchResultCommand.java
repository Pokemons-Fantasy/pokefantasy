package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

public record RecordMatchResultCommand(
        String leagueId,
        String matchId,
        String winnerUsername,
        String requestingUsername
) implements Command {
}
