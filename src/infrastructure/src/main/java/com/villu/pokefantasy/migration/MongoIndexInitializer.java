package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crea al arrancar los índices declarados en las entidades ({@code @Indexed}, {@code @CompoundIndex}).
 *
 * <p>En Spring Boot 4 la creación automática se activa con {@code spring.data.mongodb.auto-index-creation}
 * (antes el application.yml usaba {@code spring.mongodb.…}, que se ignora, y no se creaba ninguno: ni
 * siquiera el único de {@code users.name}). Aquí se hace a mano para que un índice que no se puede crear
 * (p. ej. el único si ya hay usernames duplicados) se registre como ERROR en vez de impedir el arranque.
 * Crear un índice que ya existe no hace nada.
 */
@Component
@Slf4j
public class MongoIndexInitializer {

    static final List<Class<?>> INDEXED_ENTITIES = List.of(
            UserEntity.class, LeagueEntity.class, DraftEntity.class, ScheduleEntity.class,
            TradeEntity.class, ClosedListEntity.class, ActivityEventEntity.class);

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mappingContext;

    public MongoIndexInitializer(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mappingContext = mappingContext;
    }

    @PostConstruct
    public void createIndexes() {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        int created = 0;
        for (Class<?> entity : INDEXED_ENTITIES) {
            List<? extends IndexDefinition> indexes;
            try {
                indexes = resolver.resolveIndexForEntity(mappingContext.getRequiredPersistentEntity(entity));
            } catch (RuntimeException e) {
                log.error("Could not resolve MongoDB indexes for {}: {}", entity.getSimpleName(), e.getMessage());
                continue;
            }
            for (IndexDefinition index : indexes) {
                try {
                    mongoTemplate.indexOps(entity).createIndex(index);
                    created++;
                } catch (RuntimeException e) {
                    log.error("Could not create MongoDB index {} on {}: {}",
                            index.getIndexKeys().toJson(), entity.getSimpleName(), e.getMessage());
                }
            }
        }
        log.info("MongoDB indexes ensured: {}", created);
    }
}
