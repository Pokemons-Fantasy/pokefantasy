package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Collation.ComparisonLevel;
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
    public List<ClosedListEntity> findAllByLeagueId(String leagueId) {
        return mongoTemplate.find(new Query(Criteria.where("leagueId").is(leagueId)), ClosedListEntity.class);
    }

    @Override
    public long countByNominatedByAndLeagueId(String username, String leagueId) {
        Query query = new Query(Criteria.where("nominatedBy").is(username).and("leagueId").is(leagueId));
        return mongoTemplate.count(query, ClosedListEntity.class);
    }

    @Override
    public boolean existsByPokemonNameAndLeagueId(String pokemonName, String leagueId) {
        Query query = new Query(Criteria.where("pokemonName").is(pokemonName).and("leagueId").is(leagueId))
                .collation(Collation.of("en").strength(ComparisonLevel.secondary()));
        return mongoTemplate.exists(query, ClosedListEntity.class);
    }

    @Override
    public Optional<ClosedListEntity> findByPokemonNameIgnoreCaseAndLeagueId(String pokemonName, String leagueId) {
        Query query = new Query(Criteria.where("pokemonName").is(pokemonName).and("leagueId").is(leagueId))
                .collation(Collation.of("en").strength(ComparisonLevel.secondary()));
        return Optional.ofNullable(mongoTemplate.findOne(query, ClosedListEntity.class));
    }

    @Override
    public void deleteByPokemonNameAndNominatedByAndLeagueId(String pokemonName, String username, String leagueId) {
        Query query = new Query(Criteria.where("pokemonName").is(pokemonName)
                .and("nominatedBy").is(username)
                .and("leagueId").is(leagueId));
        mongoTemplate.remove(query, ClosedListEntity.class);
    }
}
