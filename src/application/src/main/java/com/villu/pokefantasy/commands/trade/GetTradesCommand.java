package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Command;

/** @param historyLimit cuántos trades ya resueltos devolver como máximo (los pendientes van siempre). */
public record GetTradesCommand(
        String leagueId,
        String username,
        int historyLimit
) implements Command {}
