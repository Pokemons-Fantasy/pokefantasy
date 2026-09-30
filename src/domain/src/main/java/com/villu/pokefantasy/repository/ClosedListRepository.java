package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;

import java.util.List;
import java.util.Optional;

public interface ClosedListRepository {
    void save(ClosedListEntity entry);
    Optional<ClosedListEntity> findById(String id);
    void updateTier(String id, Tier tier);
    /** Deja todo el pool de la liga sin tier (vuelta a nominaciones). */
    void clearTiers(String leagueId);

    List<ClosedListEntity> findAllByLeagueId(String leagueId);
    long countByNominatedByAndLeagueId(String username, String leagueId);
    boolean existsByPokemonNameAndLeagueId(String pokemonName, String leagueId);
    Optional<ClosedListEntity> findByPokemonNameIgnoreCaseAndLeagueId(String pokemonName, String leagueId);
    void deleteByPokemonNameAndNominatedByAndLeagueId(String pokemonName, String username, String leagueId);
}
