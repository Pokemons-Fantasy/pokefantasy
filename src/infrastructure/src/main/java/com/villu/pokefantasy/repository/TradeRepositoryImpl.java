package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    public List<TradeEntity> findByLeagueIdAndParticipant(String leagueId, String username, int resolvedLimit) {
        // Pendientes: todos (están acotados). Resueltos: solo los más recientes, porque el historial
        // de una temporada no deja de crecer.
        List<TradeEntity> trades = new ArrayList<>(mongoTemplate.find(
                new Query(participant(leagueId, username).and("status").is(TradeStatus.PENDING)),
                TradeEntity.class));
        trades.addAll(mongoTemplate.find(
                new Query(participant(leagueId, username).and("status").ne(TradeStatus.PENDING))
                        .with(Sort.by(Sort.Direction.DESC, "resolvedAt"))
                        .limit(resolvedLimit),
                TradeEntity.class));
        return trades;
    }

    private static Criteria participant(String leagueId, String username) {
        return Criteria.where("leagueId").is(leagueId)
                .orOperator(Criteria.where("proposer").is(username), Criteria.where("responder").is(username));
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
