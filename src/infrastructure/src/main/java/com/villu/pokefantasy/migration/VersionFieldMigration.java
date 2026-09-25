package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inicializa {@code version = 0} en los documentos creados antes de añadir {@code @Version}.
 *
 * <p>Spring Data trata una entidad con versión {@code null} como nueva y haría un insert
 * (clave duplicada) al guardarla. Se ejecuta al arrancar, antes de aceptar peticiones,
 * y es idempotente: solo toca documentos sin el campo.
 */
@Component
@Slf4j
public class VersionFieldMigration {

    static final List<Class<?>> VERSIONED_ENTITIES = List.of(
            LeagueEntity.class, UserEntity.class, ScheduleEntity.class,
            TradeEntity.class, ClosedListEntity.class);

    private final MongoTemplate mongoTemplate;

    public VersionFieldMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void migrate() {
        Query missingVersion = new Query(Criteria.where("version").exists(false));
        Update initialVersion = new Update().set("version", 0L);
        for (Class<?> entity : VERSIONED_ENTITIES) {
            long modified = mongoTemplate.updateMulti(missingVersion, initialVersion, entity).getModifiedCount();
            if (modified > 0) {
                log.info("Initialized version field on {} {} documents", modified, entity.getSimpleName());
            }
        }
    }
}
