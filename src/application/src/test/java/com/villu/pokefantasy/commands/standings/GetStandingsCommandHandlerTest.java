package com.villu.pokefantasy.commands.standings;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.response.PlayerStandingResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStandingsCommandHandlerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private GetStandingsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetStandingsCommandHandler(scheduleRepository, leagueRepository, leagueMembershipGuard);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetStandingsCommand("l1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("l1");
    }

    @Test
    void handle_noSchedule_returnsAllZeros() {
        LeagueEntity league = leagueWithMembers("ash", "brock");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

        StandingsResponse response = handler.handle(new GetStandingsCommand("l1", "ash"));

        assertThat(response.getStandings()).hasSize(2);
        assertThat(response.getStandings()).allSatisfy(s -> {
            assertThat(s.getWins()).isZero();
            assertThat(s.getLosses()).isZero();
            assertThat(s.getPlayed()).isZero();
        });
    }

    @Test
    void handle_completedMatches_countsWinsAndLossesCorrectly() {
        LeagueEntity league = leagueWithMembers("ash", "brock", "misty");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        Match m1 = new Match("m1", "ash", "brock", "ash", MatchStatus.COMPLETED);
        Match m2 = new Match("m2", "ash", "misty", "misty", MatchStatus.COMPLETED);
        Match m3 = new Match("m3", "brock", "misty", null, MatchStatus.PENDING);
        Jornada j1 = new Jornada(1, new ArrayList<>(List.of(m1, m2, m3)), null);
        schedule.setJornadas(new ArrayList<>(List.of(j1)));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        StandingsResponse response = handler.handle(new GetStandingsCommand("l1", "ash"));

        PlayerStandingResponse ash = findPlayer(response, "ash");
        PlayerStandingResponse brock = findPlayer(response, "brock");
        PlayerStandingResponse misty = findPlayer(response, "misty");

        assertThat(ash.getWins()).isEqualTo(1);
        assertThat(ash.getLosses()).isEqualTo(1);
        assertThat(ash.getPlayed()).isEqualTo(2);

        assertThat(brock.getWins()).isZero();
        assertThat(brock.getLosses()).isEqualTo(1);
        assertThat(brock.getPlayed()).isEqualTo(1);

        assertThat(misty.getWins()).isEqualTo(1);
        assertThat(misty.getLosses()).isZero();
        assertThat(misty.getPlayed()).isEqualTo(1);
    }

    @Test
    void handle_sortOrder_winsThenCoins() {
        LeagueEntity league = leagueWithCoins("ash", 100, "brock", 200, "misty", 50);
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        // ash: 2 wins, brock: 2 wins (more coins), misty: 1 win
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        Match m1 = new Match("m1", "ash", "misty", "ash", MatchStatus.COMPLETED);
        Match m2 = new Match("m2", "ash", "brock", "ash", MatchStatus.COMPLETED);
        Match m3 = new Match("m3", "brock", "misty", "brock", MatchStatus.COMPLETED);
        Match m4 = new Match("m4", "brock", "ash", "brock", MatchStatus.COMPLETED);
        Match m5 = new Match("m5", "misty", "ash", "misty", MatchStatus.COMPLETED);
        Jornada j = new Jornada(1, new ArrayList<>(List.of(m1, m2, m3, m4, m5)), null);
        schedule.setJornadas(new ArrayList<>(List.of(j)));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        StandingsResponse response = handler.handle(new GetStandingsCommand("l1", "ash"));
        List<PlayerStandingResponse> standings = response.getStandings();

        // brock: 2W, 200 coins → 1st
        // ash: 2W, 100 coins → 2nd
        // misty: 1W → 3rd
        assertThat(standings.get(0).getUsername()).isEqualTo("brock");
        assertThat(standings.get(1).getUsername()).isEqualTo("ash");
        assertThat(standings.get(2).getUsername()).isEqualTo("misty");
    }

    @Test
    void handle_coinBalance_readFromLeagueMembers() {
        LeagueEntity league = leagueWithCoins("ash", 150, "brock", 75);
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

        StandingsResponse response = handler.handle(new GetStandingsCommand("l1", "ash"));

        assertThat(findPlayer(response, "ash").getCoins()).isEqualTo(150);
        assertThat(findPlayer(response, "brock").getCoins()).isEqualTo(75);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetStandingsCommand.class);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private LeagueEntity leagueWithMembers(String... usernames) {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        List<LeagueMember> members = new ArrayList<>();
        for (String u : usernames) {
            members.add(new LeagueMember(u, LeagueRole.USER, 0));
        }
        league.setMembers(members);
        return league;
    }

    private LeagueEntity leagueWithCoins(Object... pairs) {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        List<LeagueMember> members = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            members.add(new LeagueMember((String) pairs[i], LeagueRole.USER, (int) pairs[i + 1]));
        }
        league.setMembers(members);
        return league;
    }

    private PlayerStandingResponse findPlayer(StandingsResponse response, String username) {
        return response.getStandings().stream()
                .filter(s -> s.getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Player not found: " + username));
    }
}
