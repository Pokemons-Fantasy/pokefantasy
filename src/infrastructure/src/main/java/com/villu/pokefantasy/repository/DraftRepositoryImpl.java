package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DraftRepositoryImpl implements DraftRepository {

    private final MongoTemplate mongoTemplate;

    public DraftRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(DraftEntity draft) {
        mongoTemplate.save(draft);
    }

    @Override
    public Optional<DraftEntity> findActive() {
        Query query = new Query(Criteria.where("status").in(DraftStatus.PENDING, DraftStatus.IN_PROGRESS));
        return Optional.ofNullable(mongoTemplate.findOne(query, DraftEntity.class));
    }

    @Override
    public Optional<DraftEntity> findLatest() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, DraftEntity.class));
    }
}
