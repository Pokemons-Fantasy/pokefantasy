package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Reglas de turno y presupuesto del draft: precio de cada Pokémon, monedas que le quedan a cada jugador,
 * quién puede elegir y a quién le toca. No usa repositorios: quien llama le pasa el draft y el pool.
 * Un draft sin {@code config} (anterior a la configuración) es gratis y lineal.
 */
@Service
public class DraftTurnService {

    static final int DEFAULT_MAX_TEAM_SIZE = 10;

    /** Rondas del draft y tope de equipo: {@code maxTeamSize} de la liga, o 10 si no está configurado. */
    public int maxTeamSize(LeagueEntity league) {
        if (league != null && league.getSettings() != null) {
            Integer max = league.getSettings().getMaxTeamSize();
            if (max != null && max > 0) return max;
        }
        return DEFAULT_MAX_TEAM_SIZE;
    }

    public int priceOf(DraftEntity draft, ClosedListEntity entry) {
        DraftConfig config = draft.getConfig();
        if (config == null || entry.getTier() == null) return 0;
        Integer price = switch (entry.getTier()) {
            case S -> config.getPriceS();
            case A -> config.getPriceA();
            case B -> config.getPriceB();
            case C -> config.getPriceC();
            case D -> config.getPriceD();
        };
        return price == null ? 0 : price;
    }

    /** Monedas que le quedan a {@code username}; null si el draft no tiene presupuesto. */
    public Integer remainingBudget(DraftEntity draft, String username) {
        DraftConfig config = draft.getConfig();
        if (config == null || config.getBudget() == null) return null;
        int spent = draft.getDraftHistory() == null ? 0 : draft.getDraftHistory().stream()
                .filter(p -> username.equals(p.getUsername()) && p.getPrice() != null)
                .mapToInt(DraftPick::getPrice)
                .sum();
        return config.getBudget() - spent;
    }

    /** Pokémon del pool que aún no tienen dueño en el draft. */
    public List<ClosedListEntity> available(DraftEntity draft, List<ClosedListEntity> pool) {
        Set<String> owned = draft.ownedPokemonNames();
        return pool.stream().filter(e -> !owned.contains(e.getPokemonName().toLowerCase())).toList();
    }

    /** Puede elegir quien no tiene el equipo lleno y puede pagar al menos un Pokémon libre. */
    public boolean canPick(DraftEntity draft, String username, List<ClosedListEntity> available, int maxTeamSize) {
        if (draft.teamSize(username) >= maxTeamSize) return false;
        Integer remaining = remainingBudget(draft, username);
        if (remaining == null) return true;
        return available.stream().anyMatch(e -> priceOf(draft, e) <= remaining);
    }

    /**
     * Pasa el turno al siguiente jugador que pueda elegir (en snake, las rondas pares van hacia atrás).
     * A quien no puede elegir se le salta. Si nadie puede, o se pasa de {@code maxTeamSize} rondas,
     * el draft queda COMPLETED. Poder elegir solo empeora con el tiempo (menos dinero, menos pool, más
     * equipo), así que recorrer dos vueltas de posiciones basta para encontrar a alguien.
     */
    public void advance(DraftEntity draft, List<ClosedListEntity> available, int maxTeamSize) {
        int players = draft.getTurnOrder().size();
        int index = draft.getCurrentTurnIndex();
        int round = draft.getCurrentRound();
        for (int step = 0; step < 2 * players; step++) {
            boolean forward = isForward(draft, round);
            if (forward ? index + 1 < players : index > 0) {
                index += forward ? 1 : -1;
            } else {
                round++;
                if (round > maxTeamSize) break;
                index = isForward(draft, round) ? 0 : players - 1;
            }
            if (canPick(draft, draft.getTurnOrder().get(index), available, maxTeamSize)) {
                draft.setCurrentTurnIndex(index);
                draft.setCurrentRound(round);
                return;
            }
        }
        draft.setStatus(DraftStatus.COMPLETED);
    }

    /** Primer turno del draft: el primero del orden, o el siguiente que pueda elegir. */
    public void placeFirstTurn(DraftEntity draft, List<ClosedListEntity> available, int maxTeamSize) {
        draft.setCurrentRound(1);
        draft.setCurrentTurnIndex(0);
        if (!canPick(draft, draft.getTurnOrder().get(0), available, maxTeamSize)) {
            advance(draft, available, maxTeamSize);
        }
    }

    private static boolean isForward(DraftEntity draft, int round) {
        boolean snake = draft.getConfig() != null && Boolean.TRUE.equals(draft.getConfig().getSnake());
        return !snake || round % 2 == 1;
    }
}
