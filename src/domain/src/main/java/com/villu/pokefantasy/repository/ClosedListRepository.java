package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;

import java.util.List;
import java.util.Optional;

public interface ClosedListRepository {
    void save(ClosedListEntity entry);
    List<ClosedListEntity> findAll();
    Optional<ClosedListEntity> findById(String id);
    void updateTier(String id, Tier tier);
    long countByNominatedBy(String username);
    boolean existsByPokemonName(String pokemonName);
    Optional<ClosedListEntity> findByPokemonNameIgnoreCase(String pokemonName);
    void deleteByPokemonNameAndNominatedBy(String pokemonName, String username);
}
