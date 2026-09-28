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
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        boolean isMember = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username));

        if (!isMember) {
            throw new ForbiddenOperationException("No eres miembro de esta liga");
        }
        return league;
    }

    /** {@code true} si son el mismo usuario o están juntos en alguna liga. */
    public boolean sharesLeague(String username, String otherUsername) {
        if (username.equals(otherUsername)) {
            return true;
        }
        return leagueRepository.findByMemberUsername(username).stream()
                .anyMatch(league -> league.getMembers().stream()
                        .anyMatch(m -> m.getUsername().equals(otherUsername)));
    }
}
