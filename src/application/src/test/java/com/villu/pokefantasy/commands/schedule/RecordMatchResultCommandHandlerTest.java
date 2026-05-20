package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordMatchResultCommandHandlerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;

    private RecordMatchResultCommandHandler handler;

    private static final String LEAGUE_ID = "l1";
    private static final String MATCH_ID = "match-1";
    private static final String ADMIN = "ash";
    private static final String PLAYER1 = "ash";
    private static final String PLAYER2 = "brock";

    @BeforeEach
    void setUp() {
        handler = new RecordMatchResultCommandHandler(scheduleRepository, leagueAdminGuard);
    }

    @Test
    void handle_notAdmin_propagatesForbidden() {
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, "misty"))
                .thenThrow(new ForbiddenOperationException("not admin"));

        assertThatThrownBy(() -> handler.handle(
                new RecordMatchResultCommand(LEAGUE_ID, MATCH_ID, PLAYER1, "misty")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_noSchedule_throwsIllegalState() {
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new RecordMatchResultCommand(LEAGUE_ID, MATCH_ID, PLAYER1, ADMIN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schedule");
    }

    @Test
    void handle_matchNotFound_throwsIllegalArgument() {
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(scheduleWithMatch("other-id")));

        assertThatThrownBy(() -> handler.handle(
                new RecordMatchResultCommand(LEAGUE_ID, MATCH_ID, PLAYER1, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Match not found");
    }

    @Test
    void handle_winnerNotParticipant_throwsIllegalArgument() {
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(scheduleWithMatch(MATCH_ID)));

        assertThatThrownBy(() -> handler.handle(
                new RecordMatchResultCommand(LEAGUE_ID, MATCH_ID, "misty", ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a participant");
    }

    @Test
    void handle_validResult_savesWinnerAndCompletes() {
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(scheduleWithMatch(MATCH_ID)));

        handler.handle(new RecordMatchResultCommand(LEAGUE_ID, MATCH_ID, PLAYER1, ADMIN));

        ArgumentCaptor<ScheduleEntity> captor = ArgumentCaptor.forClass(ScheduleEntity.class);
        verify(scheduleRepository).save(captor.capture());

        Match saved = captor.getValue().getJornadas().get(0).getMatches().get(0);
        assertThat(saved.getWinnerUsername()).isEqualTo(PLAYER1);
        assertThat(saved.getStatus()).isEqualTo(MatchStatus.COMPLETED);
    }

    @Test
    void handle_player2Wins_savesCorrectly() {
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(scheduleWithMatch(MATCH_ID)));

        handler.handle(new RecordMatchResultCommand(LEAGUE_ID, MATCH_ID, PLAYER2, ADMIN));

        ArgumentCaptor<ScheduleEntity> captor = ArgumentCaptor.forClass(ScheduleEntity.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getJornadas().get(0).getMatches().get(0).getWinnerUsername())
                .isEqualTo(PLAYER2);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(RecordMatchResultCommand.class);
    }

    // --- helpers ---

    private ScheduleEntity scheduleWithMatch(String matchId) {
        Match match = new Match(matchId, PLAYER1, PLAYER2, null, MatchStatus.PENDING);
        Jornada jornada = new Jornada(1, new ArrayList<>(List.of(match)));
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setId("s1");
        schedule.setLeagueId(LEAGUE_ID);
        schedule.setJornadas(new ArrayList<>(List.of(jornada)));
        return schedule;
    }
}
