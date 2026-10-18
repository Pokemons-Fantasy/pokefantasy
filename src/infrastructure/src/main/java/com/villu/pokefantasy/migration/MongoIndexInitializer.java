package com.villu.pokefantasy.migration;

import com.mongodb.MongoException;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Crea al arrancar los índices declarados en las entidades ({@code @Indexed}, {@code @CompoundIndex}).
 *
 * <p>En Spring Boot 4 la creación automática se activa con {@code spring.data.mongodb.auto-index-creation}
 * (antes el application.yml usaba {@code spring.mongodb.…}, que se ignora, y no se creaba ninguno: ni
 * siquiera el único de {@code users.name}). Aquí se hace a mano para que un índice que no se puede crear
 * (p. ej. el único si ya hay usernames duplicados) se registre como ERROR en vez de impedir el arranque.
 * Crear un índice que ya existe no hace nada.
 *
 * <p>Si un índice ya existe con otras opciones (p. ej. pasa a {@code unique}), Mongo rechaza crearlo: se
 * borra el antiguo y se crea el nuevo. Si el nuevo no se puede crear (duplicados), se vuelve a crear sin
 * {@code unique} para no perder el índice.
 *
 * <p>Depende de {@link UserNameLowerMigration}: el índice único de {@code users.nameLower} necesita que
 * todos los usuarios lo tengan relleno.
 */
@Component
@DependsOn("userNameLowerMigration")
@Slf4j
public class MongoIndexInitializer {

    static final List<Class<?>> INDEXED_ENTITIES = List.of(
            UserEntity.class, LeagueEntity.class, DraftEntity.class, ScheduleEntity.class,
            TradeEntity.class, ClosedListEntity.class, ActivityEventEntity.class);

    /** IndexOptionsConflict e IndexKeySpecsConflict: ya hay un índice con esas claves o ese nombre. */
    private static final Set<Integer> OPTIONS_CONFLICT_CODES = Set.of(85, 86);

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
                    if (isOptionsConflict(e)) {
                        created += recreate(mongoTemplate.indexOps(entity), index, entity) ? 1 : 0;
                        continue;
                    }
                    log.error("Could not create MongoDB index {} on {}: {}",
                            index.getIndexKeys().toJson(), entity.getSimpleName(), e.getMessage());
                }
            }
        }
        log.info("MongoDB indexes ensured: {}", created);
    }

    /**
     * Borra el índice que tiene ese nombre o esas claves y crea el nuevo; si falla, lo deja sin unique.
     * Nunca lanza: devuelve si el índice quedó con las opciones declaradas.
     */
    private boolean recreate(IndexOperations indexOps, IndexDefinition index, Class<?> entity) {
        String name = index.getIndexOptions().getString("name");
        try {
            indexOps.getIndexInfo().stream()
                    .filter(existing -> existing.getName().equals(name)
                            || existing.isIndexForFields(index.getIndexKeys().keySet()))
                    .map(IndexInfo::getName)
                    .forEach(indexOps::dropIndex);
            indexOps.createIndex(index);
            log.info("Recreated MongoDB index {} on {} with new options", name, entity.getSimpleName());
            return true;
        } catch (RuntimeException e) {
            log.error("Could not recreate MongoDB index {} on {}, keeping it without unique: {}",
                    name, entity.getSimpleName(), e.getMessage());
        }
        try {
            indexOps.createIndex(withoutUnique(index));
        } catch (RuntimeException e) {
            log.error("Could not create MongoDB index {} on {}: {}", name, entity.getSimpleName(), e.getMessage());
        }
        return false;
    }

    private static IndexDefinition withoutUnique(IndexDefinition index) {
        Document options = new Document(index.getIndexOptions());
        options.remove("unique");
        return new IndexDefinition() {
            @Override
            public Document getIndexKeys() {
                return index.getIndexKeys();
            }

            @Override
            public Document getIndexOptions() {
                return options;
            }
        };
    }

    private static boolean isOptionsConflict(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo && OPTIONS_CONFLICT_CODES.contains(mongo.getCode())) {
                return true;
            }
        }
        return false;
    }
}
