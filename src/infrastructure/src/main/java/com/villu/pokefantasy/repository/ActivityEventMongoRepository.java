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
    public List<ActivityEventEntity> find(ActivityEventFilter filter, int page, int size) {
        Query query = new Query(criteria(filter))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ActivityEventEntity.class, "activity_events");
    }

    @Override
    public long count(ActivityEventFilter filter) {
        return mongoTemplate.count(new Query(criteria(filter)), ActivityEventEntity.class, "activity_events");
    }

    private static Criteria criteria(ActivityEventFilter filter) {
        Criteria criteria = Criteria.where("leagueId").is(filter.leagueId());
        if (filter.types() != null) {
            criteria.and("type").in(filter.types());
        }
        if (filter.username() != null) {
            criteria.orOperator(
                    Criteria.where("actorUsername").is(filter.username()),
                    Criteria.where("targetUsername").is(filter.username()));
        }
        return criteria;
    }
}
