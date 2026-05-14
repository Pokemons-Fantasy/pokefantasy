package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class LeagueAdminGuard {

    private final LeagueRepository leagueRepository;

    public LeagueAdminGuard(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    public LeagueEntity requireLeagueAdmin(String leagueId, String username) {
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        boolean isAdmin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username) && m.getLeagueRole() == LeagueRole.ADMIN);

        if (!isAdmin) {
            throw new ForbiddenOperationException("User '" + username + "' is not an admin of league: " + leagueId);
        }
        return league;
    }
}
