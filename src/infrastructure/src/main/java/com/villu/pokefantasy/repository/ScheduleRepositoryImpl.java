package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ScheduleRepositoryImpl implements ScheduleRepository {

    private final MongoTemplate mongoTemplate;

    public ScheduleRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(ScheduleEntity schedule) {
        try {
            mongoTemplate.save(schedule);
        } catch (Exception e) {
            log.error("Error saving schedule: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<ScheduleEntity> findByLeagueId(String leagueId) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId));
        return Optional.ofNullable(mongoTemplate.findOne(query, ScheduleEntity.class));
    }

    @Override
    public List<ScheduleEntity> findAll() {
        return mongoTemplate.findAll(ScheduleEntity.class);
    }

    @Override
    public void deleteByLeagueId(String leagueId) {
        mongoTemplate.remove(Query.query(Criteria.where("leagueId").is(leagueId)), ScheduleEntity.class);
    }

    /** Partidos anidados en jornadas: arrayFilters para tocar solo los de ese usuario. */
    @Override
    public void anonymizeUser(String username, String replacement) {
        for (String field : new String[] {"player1", "player2", "winnerUsername"}) {
            mongoTemplate.updateMulti(Query.query(Criteria.where("jornadas.matches." + field).is(username)),
                    new Update().set("jornadas.$[].matches.$[m]." + field, replacement)
                            .filterArray(Criteria.where("m." + field).is(username)).inc("version", 1),
                    ScheduleEntity.class);
        }
    }
}
