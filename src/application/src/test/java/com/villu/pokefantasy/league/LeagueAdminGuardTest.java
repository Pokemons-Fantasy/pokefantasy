package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueAdminGuardTest {

    @Mock private LeagueRepository leagueRepository;

    private LeagueAdminGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LeagueAdminGuard(leagueRepository);
    }

    @Test
    void requireLeagueAdmin_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById("league-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireLeagueAdmin("league-1", "ash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void requireLeagueAdmin_userIsNotMember_throwsForbidden() {
        LeagueEntity league = leagueWithMembers(
                new LeagueMember("brock", LeagueRole.ADMIN, 0)
        );
        when(leagueRepository.findById("league-1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> guard.requireLeagueAdmin("league-1", "ash"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireLeagueAdmin_userIsMemberButNotAdmin_throwsForbidden() {
        LeagueEntity league = leagueWithMembers(
                new LeagueMember("ash", LeagueRole.USER, 0)
        );
        when(leagueRepository.findById("league-1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> guard.requireLeagueAdmin("league-1", "ash"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireLeagueAdmin_userIsAdmin_returnsLeague() {
        LeagueEntity league = leagueWithMembers(
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0)
        );
        when(leagueRepository.findById("league-1")).thenReturn(Optional.of(league));

        LeagueEntity result = guard.requireLeagueAdmin("league-1", "ash");

        assertThat(result).isSameAs(league);
    }

    private LeagueEntity leagueWithMembers(LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId("league-1");
        league.setMembers(List.of(members));
        return league;
    }
}
