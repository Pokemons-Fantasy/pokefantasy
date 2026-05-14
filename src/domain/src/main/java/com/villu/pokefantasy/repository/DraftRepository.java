package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.DraftEntity;

import java.util.Optional;

public interface DraftRepository {
    void save(DraftEntity draft);
    Optional<DraftEntity> findActiveByLeagueId(String leagueId);
    Optional<DraftEntity> findLatestByLeagueId(String leagueId);
}
