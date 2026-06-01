package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;

import java.util.List;

public interface UserRepository {

    void saveUser(UserEntity userEntity);
    UserEntity findByUsername(String username);
    void updateUserWithPokemons(UserEntity userEntity);
    List<UserEntity> findByUsernamePrefix(String prefix);
}
