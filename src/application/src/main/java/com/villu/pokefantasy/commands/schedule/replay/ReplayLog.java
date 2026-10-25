package com.villu.pokefantasy.commands.schedule.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que interesa del log (protocolo) de un replay de Showdown: jugadores, ganador, serie y Team Preview.
 *
 * @param p1         nombre del jugador en el lado p1 (tal cual lo muestra Showdown)
 * @param p2         nombre del jugador en el lado p2
 * @param winner     nombre del ganador ({@code |win|}); {@code null} si no hay
 * @param tie        la partida acabó en empate ({@code |tie})
 * @param seriesId   id de la serie {@code Best Of} a la que pertenece; {@code null} si es una partida suelta
 * @param gameNumber número de partida dentro de la serie; {@code null} si es suelta
 * @param broughtBy  nombre del jugador → especies que llevó (Team Preview, {@code |poke|})
 */
public record ReplayLog(String p1, String p2, String winner, boolean tie, String seriesId, Integer gameNumber,
                        Map<String, List<String>> broughtBy) {

    private static final Pattern BEST_OF =
            Pattern.compile("Game (\\d+)</strong> of <a href=\"/game-bestof\\d+-([a-z0-9-]+)\"");

    public static ReplayLog parse(String log) {
        Map<String, String> players = new LinkedHashMap<>();
        Map<String, List<String>> broughtBy = new LinkedHashMap<>();
        String winner = null;
        boolean tie = false;
        String seriesId = null;
        Integer gameNumber = null;
        for (String line : log.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 2) continue;
            switch (parts[1]) {
                // La primera aparición con nombre: al final del log el servidor vuelve a mandar "|player|p1|" vacío.
                case "player" -> {
                    if (parts.length > 3 && !parts[3].isBlank()) players.putIfAbsent(parts[2], parts[3]);
                }
                case "poke" -> {
                    if (parts.length > 3) {
                        String species = parts[3].split(",", 2)[0].strip();
                        broughtBy.computeIfAbsent(parts[2], k -> new ArrayList<>()).add(species);
                    }
                }
                case "win" -> winner = parts.length > 2 ? parts[2].strip() : null;
                case "tie" -> tie = true;
                case "uhtml" -> {
                    Matcher m = BEST_OF.matcher(line);
                    if (parts.length > 2 && "bestof".equals(parts[2]) && m.find()) {
                        gameNumber = Integer.parseInt(m.group(1));
                        seriesId = m.group(2);
                    }
                }
                default -> { }
            }
        }
        // Las especies se indexan por nombre de jugador, no por lado (el lado puede cambiar entre partidas).
        Map<String, List<String>> byName = new LinkedHashMap<>();
        broughtBy.forEach((side, species) -> {
            String name = players.get(side);
            if (name != null) byName.put(name, List.copyOf(species));
        });
        return new ReplayLog(players.get("p1"), players.get("p2"), winner, tie, seriesId, gameNumber, byName);
    }

    /** El formato sin las reglas custom ({@code formato@@@reglas}); {@code null} → {@code ""}. */
    public static String baseFormat(String formatid) {
        return formatid == null ? "" : formatid.split("@@@", 2)[0];
    }
}
