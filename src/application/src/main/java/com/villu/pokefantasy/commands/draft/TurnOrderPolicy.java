package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Orden de turnos válido: exactamente los miembros de la liga, sin repetidos, con sus nombres tal como están. */
@Service
public class TurnOrderPolicy {

    public List<String> canonical(List<String> turnOrder, LeagueEntity league) {
        if (turnOrder == null || turnOrder.isEmpty()) {
            throw new IllegalArgumentException("El orden de turnos debe tener al menos un jugador");
        }

        List<String> sanitized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String username : turnOrder) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("El orden de turnos tiene un jugador sin nombre");
            }
            if (!seen.add(username.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("El orden de turnos tiene jugadores repetidos");
            }
            sanitized.add(username.trim());
        }
        return matchLeagueMembers(sanitized, league);
    }

    /**
     * El orden de turnos tiene que ser exactamente la lista de miembros: si falta alguien, se queda sin equipo;
     * si sobra alguien, recibe picks sin estar en la liga. Devuelve los nombres tal como están en la liga.
     */
    private List<String> matchLeagueMembers(List<String> turnOrder, LeagueEntity league) {
        Map<String, String> membersByKey = new LinkedHashMap<>();
        if (league.getMembers() != null) {
            league.getMembers().forEach(m -> membersByKey.put(m.getUsername().toLowerCase(Locale.ROOT), m.getUsername()));
        }

        List<String> canonical = new ArrayList<>();
        List<String> strangers = new ArrayList<>();
        for (String username : turnOrder) {
            String member = membersByKey.remove(username.toLowerCase(Locale.ROOT));
            if (member == null) {
                strangers.add(username);
            } else {
                canonical.add(member);
            }
        }

        if (!strangers.isEmpty()) {
            throw new IllegalArgumentException("No son miembros de la liga: " + String.join(", ", strangers));
        }
        if (!membersByKey.isEmpty()) {
            throw new IllegalArgumentException("Faltan en el orden de turnos: " + String.join(", ", membersByKey.values()));
        }
        return canonical;
    }
}
