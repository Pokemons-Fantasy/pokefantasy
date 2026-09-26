package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

/**
 * Cambia el ganador (y el marcador) de un partido ya registrado, o lo deshace si {@code newWinnerUsername}
 * es {@code null} (el partido vuelve a pendiente). Con el mismo ganador y otro marcador, corrige solo el
 * marcador. Solo admins de la liga.
 */
public record CorrectMatchResultCommand(
        String leagueId,
        String matchId,
        String newWinnerUsername,
        MatchScore score,
        String requestingUsername
) implements Command {

    /** Sin marcador. */
    public CorrectMatchResultCommand(String leagueId, String matchId, String newWinnerUsername,
                                     String requestingUsername) {
        this(leagueId, matchId, newWinnerUsername, null, requestingUsername);
    }
}
