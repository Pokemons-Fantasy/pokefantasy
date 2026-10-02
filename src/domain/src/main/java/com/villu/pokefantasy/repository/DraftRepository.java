package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.DraftEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DraftRepository {
    void save(DraftEntity draft);
    Optional<DraftEntity> findActiveByLeagueId(String leagueId);

    /** Borra el draft (solo se usa con un draft en preparación: no tiene picks). */
    void delete(DraftEntity draft);
    Optional<DraftEntity> findLatestByLeagueId(String leagueId);
    List<DraftEntity> findAllInProgress();
    /** Todos los drafts de esas ligas, del más reciente al más antiguo. Solo carga id, leagueId y status. */
    List<DraftEntity> findAllByLeagueIdsNewestFirst(Collection<String> leagueIds);
}
