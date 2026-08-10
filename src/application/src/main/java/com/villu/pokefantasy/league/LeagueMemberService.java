package com.villu.pokefantasy.league;

import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

@Service
public class LeagueMemberService {

    public LeagueMember requireMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equals(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Member not found: " + username));
    }
}
