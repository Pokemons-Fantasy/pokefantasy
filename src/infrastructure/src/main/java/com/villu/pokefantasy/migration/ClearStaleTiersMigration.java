package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quita el tier del pool de las ligas cuyo último draft está cancelado o que ya no tienen draft
 * ("Volver a nominaciones" borraba el preparado). Hasta back #138 cancelar no limpiaba los tiers.
 * Los tiers solo valen con el draft en preparación, en curso o completado; se recalculan por BST al preparar.
 * Idempotente: solo toca entradas que todavía tienen tier.
 */
@Component
@Slf4j
public class ClearStaleTiersMigration {

    private static final Set<DraftStatus> TIERED = EnumSet.of(DraftStatus.PENDING, DraftStatus.IN_PROGRESS, DraftStatus.COMPLETED);

    private final MongoTemplate mongoTemplate;

    public ClearStaleTiersMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void migrate() {
        Query drafts = new Query().with(Sort.by(Sort.Direction.DESC, "_id"));
        drafts.fields().include("leagueId", "status");
        Map<String, DraftStatus> latestByLeague = new HashMap<>();
        for (DraftEntity draft : mongoTemplate.find(drafts, DraftEntity.class)) {
            latestByLeague.putIfAbsent(draft.getLeagueId(), draft.getStatus());
        }
        Set<String> tieredLeagues = latestByLeague.entrySet().stream()
                .filter(e -> TIERED.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        long modified = mongoTemplate.updateMulti(
                new Query(Criteria.where("tier").exists(true).and("leagueId").nin(tieredLeagues)),
                new Update().unset("tier").inc("version", 1),
                ClosedListEntity.class).getModifiedCount();
        if (modified > 0) {
            log.info("Removed stale tier from {} pool entries (draft cancelled or gone)", modified);
        }
    }
}
