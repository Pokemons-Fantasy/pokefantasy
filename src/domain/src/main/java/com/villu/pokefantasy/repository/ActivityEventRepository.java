package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ActivityEventEntity;

import java.util.List;

public interface ActivityEventRepository {

    void save(ActivityEventEntity event);

    /** Eventos que cumplen el filtro, del más reciente al más antiguo. */
    List<ActivityEventEntity> find(ActivityEventFilter filter, int page, int size);

    long count(ActivityEventFilter filter);

    void deleteByLeagueId(String leagueId);

    /** Cambia 'username' por 'replacement' como actor y como objetivo, en cualquier liga. */
    void anonymizeUser(String username, String replacement);
}
