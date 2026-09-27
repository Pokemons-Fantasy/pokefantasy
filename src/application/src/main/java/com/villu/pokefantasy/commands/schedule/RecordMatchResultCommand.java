package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

public record RecordMatchResultCommand(
        String leagueId,
        String matchId,
        String winnerUsername,
        MatchScore score,
        String requestingUsername
) implements Command {

    /** Sin marcador. */
    public RecordMatchResultCommand(String leagueId, String matchId, String winnerUsername, String requestingUsername) {
        this(leagueId, matchId, winnerUsername, null, requestingUsername);
    }
}
