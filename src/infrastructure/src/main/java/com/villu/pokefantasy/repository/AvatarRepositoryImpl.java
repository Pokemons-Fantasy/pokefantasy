package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.AvatarEntity;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AvatarRepositoryImpl implements AvatarRepository {

    private final MongoTemplate mongoTemplate;

    public AvatarRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(AvatarEntity avatar) {
        mongoTemplate.save(avatar);
    }

    @Override
    public Optional<AvatarEntity> findByUsername(String username) {
        return Optional.ofNullable(mongoTemplate.findById(username, AvatarEntity.class));
    }

    @Override
    public void deleteByUsername(String username) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(username)), AvatarEntity.class);
    }
}
