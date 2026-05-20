package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class ScheduleRepositoryImpl implements ScheduleRepository {

    private final MongoTemplate mongoTemplate;

    public ScheduleRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(ScheduleEntity schedule) {
        try {
            mongoTemplate.save(schedule);
        } catch (Exception e) {
            log.error("Error saving schedule: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<ScheduleEntity> findByLeagueId(String leagueId) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId));
        return Optional.ofNullable(mongoTemplate.findOne(query, ScheduleEntity.class));
    }
}
