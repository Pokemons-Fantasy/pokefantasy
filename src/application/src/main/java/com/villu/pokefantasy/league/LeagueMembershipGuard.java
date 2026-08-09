package com.villu.pokefantasy.league;

import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class LeagueMembershipGuard {

    private final LeagueRepository leagueRepository;

    public LeagueMembershipGuard(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    public LeagueEntity requireMember(String leagueId, String username) {
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        boolean isMember = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username));

        if (!isMember) {
            throw new ForbiddenOperationException("User '" + username + "' is not a member of league: " + leagueId);
        }
        return league;
    }
}
