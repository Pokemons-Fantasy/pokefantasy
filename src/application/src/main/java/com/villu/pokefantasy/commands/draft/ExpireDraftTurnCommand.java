package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Command;

/** Auto-pick de un turno vencido lanzado por el servidor (sin usuario que lo pida). */
public record ExpireDraftTurnCommand(String leagueId) implements Command {}
