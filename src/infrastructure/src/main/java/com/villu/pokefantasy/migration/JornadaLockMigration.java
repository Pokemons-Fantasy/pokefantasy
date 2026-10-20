package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Pasa el bloqueo antiguo ({@code DraftPick.lockedUntil}: 7 días desde el fichaje) al bloqueo hasta fin de
 * jornada ({@code lockedThroughJornada}). No se guarda en qué jornada entró cada Pokémon, así que se deduce:
 * fichaje = {@code lockedUntil - 7 días}; si es anterior al último resultado de la jornada anterior a la activa,
 * entró en una jornada ya terminada y queda libre; si no, queda bloqueado hasta que termine la activa.
 * Sin resultados de la jornada anterior se bloquea hasta la activa (conservador).
 * Idempotente: deja {@code lockedUntil} a null, y solo toca drafts que aún lo tienen.
 */
@Component
@Slf4j
public class JornadaLockMigration {

    /** Duración del bloqueo antiguo, la que se puso al fichar. */
    static final Duration LEGACY_LOCK = Duration.ofDays(7);

    private final MongoTemplate mongoTemplate;
    private final JornadaWindowService jornadaWindowService;

    public JornadaLockMigration(MongoTemplate mongoTemplate, JornadaWindowService jornadaWindowService) {
        this.mongoTemplate = mongoTemplate;
        this.jornadaWindowService = jornadaWindowService;
    }

    @PostConstruct
    public void migrate() {
        migrate(Instant.now());
    }

    void migrate(Instant now) {
        int drafts = 0;
        for (DraftEntity draft : mongoTemplate.find(
                new Query(Criteria.where("picks.lockedUntil").exists(true)), DraftEntity.class)) {
            ScheduleEntity schedule = mongoTemplate.findOne(
                    new Query(Criteria.where("leagueId").is(draft.getLeagueId())), ScheduleEntity.class);
            Integer active = jornadaWindowService.getActiveJornada(schedule)
                    .map(ScheduleEntity.Jornada::getRoundNumber)
                    .orElse(null);
            Instant previousClosedAt = active != null && active > 1 ? lastResultAt(draft.getLeagueId(), active - 1) : null;

            for (DraftPick pick : draft.getPicks()) {
                if (pick.getLockedUntil() == null) continue;
                pick.setLockedThroughJornada(lockedThroughJornada(pick.getLockedUntil(), active, previousClosedAt, now));
                pick.setLockedUntil(null);
            }
            mongoTemplate.save(draft);
            drafts++;
        }
        if (drafts > 0) {
            log.info("Migrated legacy lockedUntil to lockedThroughJornada in {} drafts", drafts);
        }
    }

    /**
     * Jornada hasta la que queda bloqueado un pick con el {@code lockedUntil} antiguo; null = libre.
     *
     * @param active           jornada activa de la liga; null si la temporada terminó
     * @param previousClosedAt último resultado de la jornada {@code active - 1}; null si no se conoce
     */
    static Integer lockedThroughJornada(Instant lockedUntil, Integer active, Instant previousClosedAt, Instant now) {
        if (active == null || !now.isBefore(lockedUntil)) {
            return null;
        }
        Instant acquiredAt = lockedUntil.minus(LEGACY_LOCK);
        if (previousClosedAt != null && acquiredAt.isBefore(previousClosedAt)) {
            return null; // entró en una jornada que ya tiene todos sus resultados
        }
        return active;
    }

    private Instant lastResultAt(String leagueId, int roundNumber) {
        Query query = new Query(Criteria.where("leagueId").is(leagueId)
                .and("type").is(ActivityEventType.MATCH_RESULT)
                .and("roundNumber").is(roundNumber))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(1);
        ActivityEventEntity last = mongoTemplate.findOne(query, ActivityEventEntity.class);
        return last != null ? last.getCreatedAt() : null;
    }
}
