package com.villu.pokefantasy.response;

import java.util.List;

public record SeasonStatsResponse(List<PlayerSeasonStats> players) {

    public record PlayerSeasonStats(
            String username,
            int wins,
            int losses,
            int played,
            int winPct,
            int currentStreak,
            String mvpPokemon
    ) {}
}
