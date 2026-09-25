package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.DraftStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Document(collection = "draft")
public class DraftEntity {
    @Id
    private String id;
    @Version
    private Long version;
    private DraftStatus status;
    private List<String> turnOrder;
    private int currentTurnIndex;
    private int currentRound;
    private List<DraftPick> picks;
    /** Copia inmutable del draft original: cada pick tal como se eligió, sin las mutaciones
     *  posteriores de robos/swaps/trades. Alimenta el historial del draft. */
    private List<DraftPick> draftHistory;
    @Indexed
    private String leagueId;
    /** Timestamp when the current turn started; reset on each pick or auto-pick. */
    private Instant currentTurnStartedAt;

    /**
     * Nombres (en minúsculas) de todos los Pokémon que tienen dueño en la liga.
     * {@code picks} es la única fuente de verdad de los equipos: lo que no está aquí está en la banca.
     */
    public Set<String> ownedPokemonNames() {
        if (picks == null) {
            return Set.of();
        }
        return picks.stream()
                .map(pick -> pick.getPokemonName().toLowerCase())
                .collect(Collectors.toSet());
    }

    /** Número de Pokémon en el equipo actual de {@code username}. */
    public long teamSize(String username) {
        if (picks == null) {
            return 0;
        }
        return picks.stream().filter(pick -> username.equals(pick.getUsername())).count();
    }
}
