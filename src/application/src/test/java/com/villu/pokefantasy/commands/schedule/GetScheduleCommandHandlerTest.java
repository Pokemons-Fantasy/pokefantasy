package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.response.ScheduleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        Match m = new Match("m1", "ash", "brock", null, MatchStatus.PENDING);
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
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetScheduleCommand.class);
    }
}
