package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class DraftRepositoryImpl implements DraftRepository {

    private final MongoTemplate mongoTemplate;

    public DraftRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(DraftEntity draft) {
        try {
            mongoTemplate.save(draft);
        } catch (Exception e) {
            log.error("Failed to save draft", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<DraftEntity> findActive() {
        Query query = new Query(Criteria.where("status").in(DraftStatus.PENDING, DraftStatus.IN_PROGRESS));
        return Optional.ofNullable(mongoTemplate.findOne(query, DraftEntity.class));
    }
}
