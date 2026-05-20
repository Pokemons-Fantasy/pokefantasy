package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ScheduleEntity;

import java.util.Optional;

public interface ScheduleRepository {
    void save(ScheduleEntity schedule);
    Optional<ScheduleEntity> findByLeagueId(String leagueId);
}
