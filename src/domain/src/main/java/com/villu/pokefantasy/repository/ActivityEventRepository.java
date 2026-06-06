package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ActivityEventEntity;

import java.util.List;

public interface ActivityEventRepository {

    void save(ActivityEventEntity event);

    List<ActivityEventEntity> findByLeagueIdOrderByCreatedAtDesc(String leagueId, int page, int size);

    long countByLeagueId(String leagueId);

    List<ActivityEventEntity> findByLeagueIdAndUsernameOrderByCreatedAtDesc(String leagueId, String username, int page, int size);

    long countByLeagueIdAndUsername(String leagueId, String username);
}
