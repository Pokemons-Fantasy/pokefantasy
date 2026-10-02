package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.ActivityEventType;

import java.util.Set;

/**
 * Qué eventos del feed de una liga se piden.
 *
 * @param username si no es null, solo los eventos en que participa (como actor o como objetivo)
 * @param types    si no es null, solo esos tipos
 */
public record ActivityEventFilter(String leagueId, String username, Set<ActivityEventType> types) {}
