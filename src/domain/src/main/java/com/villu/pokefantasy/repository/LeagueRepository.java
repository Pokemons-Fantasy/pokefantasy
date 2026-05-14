package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;

import java.util.List;
import java.util.Optional;

public interface LeagueRepository {
    LeagueEntity save(LeagueEntity league);
    Optional<LeagueEntity> findById(String id);
    List<LeagueEntity> findByMemberUsername(String username);
    List<LeagueEntity> findAll();
    void addMember(String leagueId, LeagueMember member);
}
