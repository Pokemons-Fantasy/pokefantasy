package com.villu.pokefantasy.ports;

import java.util.List;

/** Un replay de la búsqueda de Showdown: id, los dos jugadores (nombres tal cual) y cuándo se subió (epoch s). */
public record ShowdownReplaySummary(String id, List<String> players, long uploadtime) {}
