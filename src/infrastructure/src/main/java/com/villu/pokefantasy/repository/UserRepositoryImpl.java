package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class UserRepositoryImpl implements UserRepository {

    private final MongoTemplate mongoTemplate;

    public UserRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void saveUser(UserEntity userEntity) {
        try {
            // Rely on the unique index on `name` — no TOCTOU race condition
            mongoTemplate.save(userEntity);
        } catch (DuplicateKeyException e) {
            log.warn("Intento de registro con nombre duplicado: {}", userEntity.getName());
            throw new DuplicateKeyException("Ya existe un usuario con name=" + userEntity.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public UserEntity findByUsername(String username) {
        try {
            return mongoTemplate.findOne(
                    Query.query(Criteria.where("name").is(username)), UserEntity.class);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public void updateUserWithPokemons(UserEntity userEntity) {
        mongoTemplate.save(userEntity);
    }

    @Override
    public List<UserEntity> findByUsernamePrefix(String prefix) {
        Query query = new Query(
            Criteria.where("name").regex("^" + java.util.regex.Pattern.quote(prefix), "i")
        ).limit(20);
        return mongoTemplate.find(query, UserEntity.class);
    }

    @Override
    public void addFcmToken(String username, String token) {
        Query query = new Query(Criteria.where("name").is(username));
        Update update = new Update().addToSet("fcmTokens", token);
        mongoTemplate.updateFirst(query, update, UserEntity.class);
    }
}
