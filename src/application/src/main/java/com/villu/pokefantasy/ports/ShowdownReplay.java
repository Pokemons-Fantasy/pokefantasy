package com.villu.pokefantasy.ports;

import java.util.List;

/**
 * Un replay completo de Showdown. {@code formatid} es el formato base (las reglas custom van en el log, líneas
 * {@code |rule|}); {@code log} es el protocolo de la partida; {@code uploadtime} en epoch s.
 */
public record ShowdownReplay(String id, String formatid, List<String> players, String log, long uploadtime) {}
