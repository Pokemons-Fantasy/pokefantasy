package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.repository.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class UserRepositoryImpl implements UserRepository {

    @Autowired
    MongoTemplate mongoTemplate;


    @Override
    public void saveUser(UserEntity userEntity) {
        try {
            Query existsQuery = Query.query(Criteria.where("name").is(userEntity.getName()));
            boolean exists = mongoTemplate.exists(existsQuery, UserEntity.class);

            if (exists) {
                throw new DuplicateKeyException("Ya existe un usuario con name=" + userEntity.getName());
            }

            mongoTemplate.save(userEntity);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public UserEntity findByUsername(String username) {
        try {
            return mongoTemplate.findOne(
                    Query.query(
                            Criteria.where("name").is(username)
                    ), UserEntity.class);
        }catch (Exception e){
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public void updateUserWithPokemons(UserEntity userEntity) {
        mongoTemplate.save(userEntity);
    }


}
