package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Estado del draft vigente de cada liga: el activo (PENDING o IN_PROGRESS) y, si no hay,
 * el más reciente. Es la misma regla que usa GetDraftStatus, así que la fase de la liga
 * coincide con lo que muestra la pantalla del draft.
 */
@Service
public class CurrentDraftService {

    private static final Set<DraftStatus> ACTIVE = EnumSet.of(DraftStatus.PENDING, DraftStatus.IN_PROGRESS);

    private final DraftRepository draftRepository;

    public CurrentDraftService(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    /** Una sola consulta para todas las ligas; las ligas sin draft no aparecen en el mapa. */
    public Map<String, DraftStatus> statusByLeague(Collection<String> leagueIds) {
        if (leagueIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DraftStatus> latest = new HashMap<>();
        Map<String, DraftStatus> active = new HashMap<>();
        for (DraftEntity draft : draftRepository.findAllByLeagueIdsNewestFirst(leagueIds)) {
            latest.putIfAbsent(draft.getLeagueId(), draft.getStatus());
            if (ACTIVE.contains(draft.getStatus())) {
                active.putIfAbsent(draft.getLeagueId(), draft.getStatus());
            }
        }
        latest.putAll(active);
        return latest;
    }

    public Optional<DraftStatus> statusOf(String leagueId) {
        return Optional.ofNullable(statusByLeague(List.of(leagueId)).get(leagueId));
    }
}
