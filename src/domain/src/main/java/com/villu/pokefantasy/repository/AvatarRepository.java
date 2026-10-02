package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.AvatarEntity;

import java.util.Optional;

public interface AvatarRepository {

    /** Crea o sobrescribe la foto del usuario ({@code _id} = username). */
    void save(AvatarEntity avatar);
    Optional<AvatarEntity> findByUsername(String username);
    void deleteByUsername(String username);
}
