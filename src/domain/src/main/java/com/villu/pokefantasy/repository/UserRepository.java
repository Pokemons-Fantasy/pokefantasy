package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;

import java.util.Collection;
import java.util.List;

public interface UserRepository {

    void saveUser(UserEntity userEntity);
    UserEntity findByUsername(String username);
    /** Si ya hay un usuario con ese nombre, sin distinguir mayúsculas ({@code Lobon} y {@code lobon}). */
    boolean existsByUsernameIgnoreCase(String username);
    List<UserEntity> findByUsernamePrefix(String prefix);
    /** Usuarios con esos nombres; solo trae {@code name} y {@code avatarVersion}. */
    List<UserEntity> findByUsernames(Collection<String> usernames);
    void setAvatarVersion(String username, Long avatarVersion);
    /** Añade el token al final y deja solo los {@code maxTokens} más recientes. */
    void addFcmToken(String username, String token, int maxTokens);
    void removeFcmToken(String token);

    /** Quita el token solo a ese usuario (dar de baja un navegador). */
    void removeFcmToken(String username, String token);

    void deleteByUsername(String username);
}
