package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TradeRepositoryImpl implements TradeRepository {

    private final MongoTemplate mongoTemplate;

    public TradeRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public TradeEntity save(TradeEntity trade) {
        try {
            return mongoTemplate.save(trade);
        } catch (Exception e) {
            log.error("Error saving trade: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<TradeEntity> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, TradeEntity.class));
    }

    @Override
    public List<TradeEntity> findByLeagueIdAndParticipant(String leagueId, String username) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId)
                .orOperator(
                        Criteria.where("proposer").is(username),
                        Criteria.where("responder").is(username)));
        return mongoTemplate.find(query, TradeEntity.class);
    }

    @Override
    public List<TradeEntity> findPendingByLeagueId(String leagueId) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId)
                .and("status").is(TradeStatus.PENDING));
        return mongoTemplate.find(query, TradeEntity.class);
    }

    @Override
    public List<TradeEntity> findPendingByResponder(String username) {
        Query query = new Query(Criteria.where("responder").is(username)
                .and("status").is(TradeStatus.PENDING));
        return mongoTemplate.find(query, TradeEntity.class);
    }
}
