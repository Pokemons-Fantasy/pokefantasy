package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.repository.entity.UserEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Elimina el campo obsoleto {@code users.pokemons}. Los equipos viven solo en
 * {@code draft.picks}; la copia en el usuario se desincronizaba (p. ej. al cancelar un draft).
 * Idempotente: solo toca documentos que todavía tienen el campo.
 */
@Component
@Slf4j
public class DropUserPokemonsMigration {

    static final String LEGACY_FIELD = "pokemons";

    private final MongoTemplate mongoTemplate;

    public DropUserPokemonsMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void migrate() {
        long modified = mongoTemplate.updateMulti(
                new Query(Criteria.where(LEGACY_FIELD).exists(true)),
                new Update().unset(LEGACY_FIELD),
                UserEntity.class).getModifiedCount();
        if (modified > 0) {
            log.info("Removed legacy '{}' field from {} users", LEGACY_FIELD, modified);
        }
    }
}
