package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.UserEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    void saveUser(UserEntity userEntity);
    UserEntity findByUsername(String username);
    /** Si ya hay un usuario con ese nombre, sin distinguir mayúsculas ({@code Lobon} y {@code lobon}). */
    boolean existsByUsernameIgnoreCase(String username);
    List<UserEntity> findByUsernamePrefix(String prefix);
    /** Usuarios con esos nombres; solo trae {@code name}, {@code avatarVersion} y {@code showdownName}. */
    List<UserEntity> findByUsernames(Collection<String> usernames);
    void setAvatarVersion(String username, Long avatarVersion);
    /** Usuario que tiene vinculada esa cuenta de Showdown. */
    Optional<UserEntity> findByShowdownId(String showdownId);
    void setShowdownAccount(String username, String showdownName, String showdownId);
    /** Quita los dos campos ({@code $unset}): con {@code null}, dos desvinculados chocarían en el índice único. */
    void clearShowdownAccount(String username);
    /** Añade el token al final y deja solo los {@code maxTokens} más recientes. */
    void addFcmToken(String username, String token, int maxTokens);
    void removeFcmToken(String token);

    /** Quita el token solo a ese usuario (dar de baja un navegador). */
    void removeFcmToken(String username, String token);

    void deleteByUsername(String username);
}
