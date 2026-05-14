package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
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
public class LeagueRepositoryImpl implements LeagueRepository {

    private final MongoTemplate mongoTemplate;

    public LeagueRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public LeagueEntity save(LeagueEntity league) {
        return mongoTemplate.save(league);
    }

    @Override
    public Optional<LeagueEntity> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, LeagueEntity.class));
    }

    @Override
    public List<LeagueEntity> findByMemberUsername(String username) {
        Query query = new Query(Criteria.where("members.username").is(username));
        return mongoTemplate.find(query, LeagueEntity.class);
    }

    @Override
    public List<LeagueEntity> findAll() {
        return mongoTemplate.findAll(LeagueEntity.class);
    }

    @Override
    public void addMember(String leagueId, LeagueMember member) {
        Query query = new Query(Criteria.where("_id").is(leagueId));
        Update update = new Update().push("members", member);
        mongoTemplate.updateFirst(query, update, LeagueEntity.class);
    }
}
