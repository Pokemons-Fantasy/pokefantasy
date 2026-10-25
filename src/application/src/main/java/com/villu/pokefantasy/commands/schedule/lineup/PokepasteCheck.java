package com.villu.pokefantasy.commands.schedule.lineup;

import com.villu.pokefantasy.commands.users.showdown.ShowdownIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compara un paste de pokepast.es (texto exportado de Showdown) con la alineación declarada. Solo da
 * avisos: los nombres de formas de PokeAPI y de Showdown no siempre coinciden.
 */
public final class PokepasteCheck {

    private static final Pattern PASTE_URL = Pattern.compile("^https://pokepast\\.es/([a-z0-9]+)/?$");
    private static final Pattern NICKNAMED = Pattern.compile("^.*\\((.+)\\)$");

    private PokepasteCheck() {}

    /** Id del paste si la URL es de pokepast.es (https); vacío para cualquier otra. */
    public static Optional<String> pasteId(String url) {
        if (url == null) return Optional.empty();
        Matcher m = PASTE_URL.matcher(url.trim());
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * Especie de cada Pokémon del paste: la primera línea de cada bloque, sin el objeto ({@code @ ...}), sin
     * el género ({@code (M)} / {@code (F)}) y, si tiene apodo ({@code Apodo (Especie)}), lo de dentro del
     * paréntesis.
     */
    public static List<String> species(String pasteText) {
        List<String> result = new ArrayList<>();
        for (String block : pasteText.replace("\r\n", "\n").split("\n\\s*\n")) {
            String line = block.strip().split("\n", 2)[0].strip();
            if (line.isEmpty()) continue;
            int at = line.indexOf(" @ ");
            if (at >= 0) line = line.substring(0, at).strip();
            line = line.replaceFirst(" \\((M|F)\\)$", "");
            Matcher nicknamed = NICKNAMED.matcher(line);
            result.add(nicknamed.matches() ? nicknamed.group(1).strip() : line);
        }
        return result;
    }

    /** Avisos que se guardan como mucho: el paste lo escribe el usuario y viaja en el calendario de toda la liga. */
    static final int MAX_WARNINGS = 12;
    static final int MAX_NAME = 40;

    /**
     * Lo que falta en el paste (en el orden de la alineación) y lo que sobra (en el orden del paste). Como
     * mucho {@link #MAX_WARNINGS} avisos más uno que cuenta el resto, y cada nombre recortado a {@link #MAX_NAME}.
     */
    public static List<String> warnings(List<String> lineup, List<String> pasteSpecies) {
        Set<String> pasteIds = pasteSpecies.stream().map(ShowdownIds::toId).collect(Collectors.toSet());
        Set<String> lineupIds = lineup.stream().map(ShowdownIds::toId).collect(Collectors.toSet());
        List<String> warnings = new ArrayList<>();
        lineup.stream().filter(name -> !pasteIds.contains(ShowdownIds.toId(name)))
                .forEach(name -> warnings.add("En el paste falta " + shorten(name) + "."));
        pasteSpecies.stream().filter(name -> !lineupIds.contains(ShowdownIds.toId(name)))
                .forEach(name -> warnings.add("El paste tiene " + shorten(name) + ", que no está en la alineación."));
        if (warnings.size() <= MAX_WARNINGS) {
            return warnings;
        }
        List<String> capped = new ArrayList<>(warnings.subList(0, MAX_WARNINGS));
        capped.add("…y " + (warnings.size() - MAX_WARNINGS) + " avisos más.");
        return capped;
    }

    private static String shorten(String name) {
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) + "…" : name;
    }
}
