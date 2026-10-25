package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

/** Consulta: lo que hace falta del partido para buscar y evaluar sus replays (y quién puede hacerlo). */
public record PrepareReplayImportCommand(String leagueId, String matchId, String username) implements Command {}
