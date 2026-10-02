package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.mediator.Command;

import java.util.List;

/** {@code turnOrder} null = no cambia el orden de turnos. */
public record UpdateDraftConfigCommand(String leagueId, String requestingUsername, DraftConfig config,
                                       List<String> turnOrder) implements Command {}
