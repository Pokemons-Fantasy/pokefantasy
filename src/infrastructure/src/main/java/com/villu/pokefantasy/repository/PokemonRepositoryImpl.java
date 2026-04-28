package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.PokemonEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PokemonRepositoryImpl implements PokemonRepository {

    MongoTemplate mongoTemplate;

    @Override
    public void addPokemons(List<PokemonEntity> pokemonEntity) {
        try {
            mongoTemplate.save(pokemonEntity);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

    }

    @Override
    public List<PokemonEntity> getAllPokemons() {
        try {
            return mongoTemplate.findAll(PokemonEntity.class);
        }catch (Exception e){
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }


}
