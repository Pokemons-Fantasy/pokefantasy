package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.PokemonEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PokemonRepositoryImpl implements PokemonRepository {

    private final MongoTemplate mongoTemplate;

    public PokemonRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void addPokemons(List<PokemonEntity> pokemonEntity) {
        try {
            if (pokemonEntity == null || pokemonEntity.isEmpty()) {
                return;
            }

            BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PokemonEntity.class);
            for (PokemonEntity pokemon : pokemonEntity) {
                if (pokemon.getId() == null || pokemon.getId().isBlank()) {
                    bulkOperations.insert(pokemon);
                } else {
                    Query query = Query.query(Criteria.where("id").is(pokemon.getId()));
                    Update update = new Update()
                            .set("name", pokemon.getName())
                            .set("url", pokemon.getUrl());
                    bulkOperations.upsert(query, update);
                }
            }
            bulkOperations.execute();
        } catch (Exception e) {
            log.error("Failed to add pokemons", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PokemonEntity> getAllPokemons() {
        try {
            return mongoTemplate.findAll(PokemonEntity.class);
        } catch (Exception e) {
            log.error("Failed to get all pokemons", e);
            throw new RuntimeException(e);
        }
    }
}
