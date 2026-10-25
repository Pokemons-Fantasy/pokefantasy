package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Command;

import java.util.List;

/** {@code pasteWarnings}: diferencias del paste con la alineación, calculadas por la facade antes del comando. */
public record DeclareLineupCommand(String leagueId, String matchId, String username, List<String> pokemonNames,
                                   String pasteUrl, List<String> pasteWarnings) implements Command {}
