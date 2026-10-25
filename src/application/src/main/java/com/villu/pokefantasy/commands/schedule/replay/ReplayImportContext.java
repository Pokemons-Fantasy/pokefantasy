package com.villu.pokefantasy.commands.schedule.replay;

import com.villu.pokefantasy.repository.entity.ScheduleEntity.MatchLineup;

import java.util.Set;

/**
 * Lo que hace falta saber de un partido para evaluar sus replays, leído de la base de datos.
 *
 * @param player1        usuario de la liga en el lado player1
 * @param player2        usuario de la liga en el lado player2
 * @param player1Id      {@code showdownId} de player1
 * @param player2Id      {@code showdownId} de player2
 * @param baseFormat     formato base de Showdown de la liga ({@code gen9doublescustomgame} / {@code gen9customgame})
 * @param fromEpoch      replays subidos desde aquí (lunes 00:00 de la semana de la jornada); {@code null} = sin filtro
 * @param usedReplayIds  replays ya usados en otros partidos de la liga
 * @param player1Lineup  alineación declarada de player1, o {@code null}
 * @param player2Lineup  alineación declarada de player2, o {@code null}
 * @param bestOf         partidas de la serie (1 o 3)
 * @param marketClosedEpoch cierre del mercado de la jornada (epoch en segundos); {@code null} sin fecha
 */
public record ReplayImportContext(String player1, String player2, String player1Id, String player2Id,
                                  String baseFormat, Long fromEpoch, Set<String> usedReplayIds,
                                  MatchLineup player1Lineup, MatchLineup player2Lineup, int bestOf,
                                  Long marketClosedEpoch) {}
