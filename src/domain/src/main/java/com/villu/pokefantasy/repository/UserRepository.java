package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;

import java.util.Collection;
import java.util.List;

public interface UserRepository {

    void saveUser(UserEntity userEntity);
    UserEntity findByUsername(String username);
    List<UserEntity> findByUsernamePrefix(String prefix);
    /** Usuarios con esos nombres; solo trae {@code name} y {@code avatarVersion}. */
    List<UserEntity> findByUsernames(Collection<String> usernames);
    void setAvatarVersion(String username, Long avatarVersion);
    void addFcmToken(String username, String token);
    void removeFcmToken(String token);
}
