package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.repository.entity.UserEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Rellena {@code users.nameLower} (= {@code name} en minúsculas) en los usuarios creados antes de que
 * existiera, para que la búsqueda por prefijo los encuentre. Idempotente: solo toca los que no lo tienen.
 */
@Component
@Slf4j
public class UserNameLowerMigration {

    private final MongoTemplate mongoTemplate;

    public UserNameLowerMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void migrate() {
        long modified = mongoTemplate.updateMulti(
                new Query(Criteria.where("nameLower").exists(false).and("name").exists(true)),
                AggregationUpdate.update().set("nameLower").toValue(StringOperators.valueOf("name").toLower()),
                UserEntity.class).getModifiedCount();
        if (modified > 0) {
            log.info("Filled nameLower for {} users", modified);
        }
    }
}
