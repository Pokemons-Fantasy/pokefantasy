package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

/**
 * Mueve la jornada {@code fromRound} y todas las siguientes {@code weeks} semanas (negativas para adelantar).
 * Solo el admin de la liga.
 */
public record ShiftScheduleCommand(
        String leagueId,
        Integer fromRound,
        Integer weeks,
        String requestingUsername
) implements Command {
}
