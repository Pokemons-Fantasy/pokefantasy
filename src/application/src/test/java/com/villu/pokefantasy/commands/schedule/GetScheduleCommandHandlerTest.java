package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.MatchLineup;
import com.villu.pokefantasy.response.ScheduleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetScheduleCommandHandlerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private JornadaWindowService jornadaWindowService;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private GetScheduleCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetScheduleCommandHandler(scheduleRepository, leagueRepository, jornadaWindowService, leagueMembershipGuard);
    }

    @Test
    void handle_noSchedule_returnsNull() {
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.empty());

        assertThat(handler.handle(new GetScheduleCommand("l1", "ash"))).isNull();
    }

    @Test
    void handle_propagatesWindowFlagsFromService() {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        Match m = new Match("m1", "ash", "brock", "ash", MatchStatus.COMPLETED);
        m.setWinnerScore(3);
        m.setLoserScore(1);
        // startDate null → toResponse no calcula deadlines, solo las flags de ventana
        Jornada j = new Jornada(1, new ArrayList<>(List.of(m)), null);
        schedule.setJornadas(new ArrayList<>(List.of(j)));

        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());
        when(jornadaWindowService.isStealWindowOpen(schedule, null)).thenReturn(true);
        when(jornadaWindowService.isSwapWindowOpen(schedule, null)).thenReturn(false);

        ScheduleResponse response = handler.handle(new GetScheduleCommand("l1", "ash"));

        assertThat(response.getLeagueId()).isEqualTo("l1");
        assertThat(response.getJornadas()).hasSize(1);
        assertThat(response.isStealWindowOpen()).isTrue();
        assertThat(response.isSwapWindowOpen()).isFalse();
        ScheduleResponse.MatchResponse match = response.getJornadas().get(0).getMatches().get(0);
        assertThat(match.getWinnerScore()).isEqualTo(3);
        assertThat(match.getLoserScore()).isEqualTo(1);
    }

    // ── Alineaciones ────────────────────────────────────────────────────────────

    private static final MatchLineup ASH_LINEUP =
            new MatchLineup(List.of("pikachu"), "https://pokepast.es/a", List.of("En el paste falta x."), Instant.EPOCH);
    private static final MatchLineup MISTY_LINEUP = new MatchLineup(List.of("starmie"), null, List.of(), Instant.EPOCH);

    /** Partido ash vs misty en una jornada sin fecha (el mock no calcula cierres). */
    private ScheduleEntity scheduleWith(Match match) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(new Jornada(1, new ArrayList<>(List.of(match)), null))));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());
        return schedule;
    }

    private ScheduleResponse.MatchResponse matchSeenBy(String username) {
        return handler.handle(new GetScheduleCommand("l1", username)).getJornadas().get(0).getMatches().get(0);
    }

    @Test
    void lineup_beforeReveal_onlyOwnIsVisible() {
        Match match = new Match("m1", "ash", "misty", null, MatchStatus.PENDING);
        match.setPlayer1Lineup(ASH_LINEUP);
        scheduleWith(match);

        ScheduleResponse.MatchResponse ash = matchSeenBy("ash");
        assertThat(ash.isPlayer1LineupSubmitted()).isTrue();
        assertThat(ash.isPlayer2LineupSubmitted()).isFalse();
        assertThat(ash.getPlayer1Lineup().getPokemonNames()).containsExactly("pikachu");
        assertThat(ash.getPlayer1Lineup().getPasteUrl()).isEqualTo("https://pokepast.es/a");
        assertThat(ash.getPlayer1Lineup().getPasteWarnings()).containsExactly("En el paste falta x.");

        ScheduleResponse.MatchResponse misty = matchSeenBy("misty");
        assertThat(misty.isPlayer1LineupSubmitted()).isTrue();
        assertThat(misty.getPlayer1Lineup()).isNull();

        assertThat(matchSeenBy("brock").getPlayer1Lineup()).isNull();
    }

    @Test
    void lineup_afterReveal_everyoneSeesBoth() {
        Match match = new Match("m1", "ash", "misty", null, MatchStatus.PENDING);
        match.setPlayer1Lineup(ASH_LINEUP);
        match.setPlayer2Lineup(MISTY_LINEUP);
        scheduleWith(match);

        ScheduleResponse.MatchResponse brock = matchSeenBy("brock");
        assertThat(brock.getPlayer1Lineup().getPokemonNames()).containsExactly("pikachu");
        assertThat(brock.getPlayer2Lineup().getPokemonNames()).containsExactly("starmie");
        assertThat(brock.isLineupOpen()).isFalse();
    }

    @Test
    void lineupOpen_onlyForPlayersWhilePeriodOpenAndNotRevealed() {
        Match match = new Match("m1", "ash", "misty", null, MatchStatus.PENDING);
        ScheduleEntity schedule = scheduleWith(match);
        when(jornadaWindowService.isLineupPeriodOpen(schedule, schedule.getJornadas().get(0), null)).thenReturn(true);

        assertThat(matchSeenBy("ash").isLineupOpen()).isTrue();
        assertThat(matchSeenBy("brock").isLineupOpen()).isFalse();

        match.setPlayer1Lineup(ASH_LINEUP);
        match.setPlayer2Lineup(MISTY_LINEUP);
        assertThat(matchSeenBy("ash").isLineupOpen()).isFalse();
    }

    @Test
    void lineupOpensAt_fromWindowService() {
        Match match = new Match("m1", "ash", "misty", null, MatchStatus.PENDING);
        ScheduleEntity schedule = scheduleWith(match);
        when(jornadaWindowService.lineupOpensAt(schedule.getJornadas().get(0), null))
                .thenReturn(Optional.of(LocalDateTime.of(2026, 6, 5, 16, 0)));

        assertThat(handler.handle(new GetScheduleCommand("l1", "ash")).getJornadas().get(0).getLineupOpensAt())
                .isEqualTo("2026-06-05T16:00");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetScheduleCommand.class);
    }
}
