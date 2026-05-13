package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class ClosedListRepositoryImpl implements ClosedListRepository {

    private final MongoTemplate mongoTemplate;

    public ClosedListRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(ClosedListEntity entry) {
        try {
            mongoTemplate.save(entry);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ClosedListEntity> findAll() {
        return mongoTemplate.findAll(ClosedListEntity.class);
    }

    @Override
    public Optional<ClosedListEntity> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, ClosedListEntity.class));
    }

    @Override
    public void updateTier(String id, Tier tier) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().set("tier", tier);
        mongoTemplate.updateFirst(query, update, ClosedListEntity.class);
    }

    @Override
    public long countByNominatedBy(String username) {
        Query query = new Query(Criteria.where("nominatedBy").is(username));
        return mongoTemplate.count(query, ClosedListEntity.class);
    }

    @Override
    public boolean existsByPokemonName(String pokemonName) {
        Query query = new Query(Criteria.where("pokemonName").is(pokemonName));
        return mongoTemplate.exists(query, ClosedListEntity.class);
    }
}
