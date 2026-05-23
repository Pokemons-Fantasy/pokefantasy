package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ActivityEventMongoRepository implements ActivityEventRepository {

    private final MongoTemplate mongoTemplate;

    public ActivityEventMongoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(ActivityEventEntity event) {
        try {
            mongoTemplate.save(event, "activity_events");
        } catch (Exception e) {
            log.error("Error saving activity event: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ActivityEventEntity> findByLeagueIdOrderByCreatedAtDesc(String leagueId, int page, int size) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ActivityEventEntity.class, "activity_events");
    }

    @Override
    public long countByLeagueId(String leagueId) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId));
        return mongoTemplate.count(query, ActivityEventEntity.class, "activity_events");
    }
}
