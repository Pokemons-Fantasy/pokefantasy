package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;
import com.villu.pokefantasy.ports.ShowdownReplay;

import java.util.List;

/** {@code replays}: las partidas marcadas, descargadas de nuevo por la facade (nada viene del cliente salvo los ids). */
public record ImportSeriesCommand(String leagueId, String matchId, String username, List<ShowdownReplay> replays)
        implements Command {}
