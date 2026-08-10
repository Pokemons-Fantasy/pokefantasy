package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeagueMemberServiceTest {

    private final LeagueMemberService service = new LeagueMemberService();

    @Test
    void requireMember_memberExists_returnsMember() {
        LeagueMember ash = new LeagueMember("ash", LeagueRole.USER, 100);
        LeagueEntity league = leagueWithMembers(ash, new LeagueMember("brock", LeagueRole.ADMIN, 0));

        LeagueMember result = service.requireMember(league, "ash");

        assertThat(result).isSameAs(ash);
    }

    @Test
    void requireMember_memberMissing_throwsIllegalState() {
        LeagueEntity league = leagueWithMembers(new LeagueMember("brock", LeagueRole.ADMIN, 0));

        assertThatThrownBy(() -> service.requireMember(league, "ash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Member not found: ash");
    }

    private LeagueEntity leagueWithMembers(LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId("league-1");
        league.setMembers(List.of(members));
        return league;
    }
}
