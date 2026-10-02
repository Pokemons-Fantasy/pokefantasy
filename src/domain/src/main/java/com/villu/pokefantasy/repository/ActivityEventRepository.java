package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ActivityEventEntity;

import java.util.List;

public interface ActivityEventRepository {

    void save(ActivityEventEntity event);

    /** Eventos que cumplen el filtro, del más reciente al más antiguo. */
    List<ActivityEventEntity> find(ActivityEventFilter filter, int page, int size);

    long count(ActivityEventFilter filter);
}
