package com.villu.pokefantasy.commands.activity;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.mediator.Command;

import java.util.Set;

/**
 * @param username si no es null ni está en blanco, solo los eventos de ese jugador
 * @param types    si no es null ni está vacío, solo esos tipos
 */
public record GetActivityFeedCommand(
        String leagueId,
        String username,
        Set<ActivityEventType> types,
        int page,
        int size,
        String requestingUsername
) implements Command {}
