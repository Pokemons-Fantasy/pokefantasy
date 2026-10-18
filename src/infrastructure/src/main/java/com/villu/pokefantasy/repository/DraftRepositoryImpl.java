package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
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
    public void delete(DraftEntity draft) {
        mongoTemplate.remove(draft);
    }

    @Override
    public Optional<DraftEntity> findActiveByLeagueId(String leagueId) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId)
                .and("status").in(DraftStatus.PENDING, DraftStatus.IN_PROGRESS));
        return Optional.ofNullable(mongoTemplate.findOne(query, DraftEntity.class));
    }

    @Override
    public Optional<DraftEntity> findLatestByLeagueId(String leagueId) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId))
                .with(Sort.by(Sort.Direction.DESC, "_id")).limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, DraftEntity.class));
    }

    @Override
    public List<DraftEntity> findAllInProgress() {
        return mongoTemplate.find(new Query(Criteria.where("status").is(DraftStatus.IN_PROGRESS)), DraftEntity.class);
    }

    @Override
    public List<DraftEntity> findAllByLeagueIdsNewestFirst(Collection<String> leagueIds) {
        Query query = new Query(Criteria.where("leagueId").in(leagueIds))
                .with(Sort.by(Sort.Direction.DESC, "_id"));
        // Solo lo necesario para decidir el estado: los picks de cada draft pesan
        query.fields().include("leagueId", "status");
        return mongoTemplate.find(query, DraftEntity.class);
    }

    @Override
    public List<DraftEntity> findAllByLeagueId(String leagueId) {
        return mongoTemplate.find(Query.query(Criteria.where("leagueId").is(leagueId)), DraftEntity.class);
    }

    @Override
    public void anonymizeUser(String username, String replacement) {
        for (String list : new String[] {"picks", "draftHistory"}) {
            mongoTemplate.updateMulti(Query.query(Criteria.where(list + ".username").is(username)),
                    new Update().set(list + ".$[p].username", replacement)
                            .filterArray(Criteria.where("p.username").is(username)).inc("version", 1),
                    DraftEntity.class);
        }
        mongoTemplate.updateMulti(Query.query(Criteria.where("turnOrder").is(username)),
                new Update().set("turnOrder.$[t]", replacement)
                        .filterArray(Criteria.where("t").is(username)).inc("version", 1),
                DraftEntity.class);
    }
}
