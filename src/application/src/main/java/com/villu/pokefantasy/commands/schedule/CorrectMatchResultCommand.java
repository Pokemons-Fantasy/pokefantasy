package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

/**
 * Cambia el ganador de un partido ya registrado, o lo deshace si {@code newWinnerUsername} es
 * {@code null} (el partido vuelve a pendiente). Solo admins de la liga.
 */
public record CorrectMatchResultCommand(
        String leagueId,
        String matchId,
        String newWinnerUsername,
        String requestingUsername
) implements Command {
}
