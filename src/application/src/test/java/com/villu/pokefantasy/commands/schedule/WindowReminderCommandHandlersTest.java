package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WindowReminderCommandHandlersTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private WindowReminderService windowReminderService;

    private ScheduleEntity dueSchedule;
    private ScheduleEntity quietSchedule;
    private LeagueEntity dueLeague;

    @BeforeEach
    void setUp() {
        dueSchedule = schedule("l1");
        quietSchedule = schedule("l2");
        dueLeague = league("l1");
        LeagueEntity quietLeague = league("l2");
        lenient().when(leagueRepository.findById("l1")).thenReturn(Optional.of(dueLeague));
        lenient().when(leagueRepository.findById("l2")).thenReturn(Optional.of(quietLeague));
        lenient().when(windowReminderService.due(dueSchedule, dueLeague.getSettings())).thenReturn(List.of(
                new WindowReminderService.Due(WindowReminderService.Window.STEAL, null, LocalDateTime.now())));
        lenient().when(windowReminderService.due(quietSchedule, quietLeague.getSettings())).thenReturn(List.of());
    }

    private static ScheduleEntity schedule(String leagueId) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(leagueId);
        return schedule;
    }

    private static LeagueEntity league(String id) {
        LeagueEntity league = new LeagueEntity();
        league.setId(id);
        league.setSettings(LeagueSettings.defaults());
        return league;
    }

    @Test
    void list_returnsOnlyLeaguesWithSomethingDue() {
        when(scheduleRepository.findAll()).thenReturn(List.of(dueSchedule, quietSchedule, schedule("orphan")));
        when(leagueRepository.findById("orphan")).thenReturn(Optional.empty());

        ListDueWindowRemindersCommandHandler handler =
                new ListDueWindowRemindersCommandHandler(scheduleRepository, leagueRepository, windowReminderService);

        assertThat(handler.handle(new ListDueWindowRemindersCommand())).containsExactly("l1");
        assertThat(handler.commandType()).isEqualTo(ListDueWindowRemindersCommand.class);
    }

    @Test
    void send_savesScheduleOnlyWhenSomethingWasSent() {
        SendWindowRemindersCommandHandler handler =
                new SendWindowRemindersCommandHandler(scheduleRepository, leagueRepository, windowReminderService);
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(dueSchedule));
        when(windowReminderService.sendDue(dueSchedule, dueLeague)).thenReturn(true);

        assertThat(handler.handle(new SendWindowRemindersCommand("l1"))).isTrue();
        verify(scheduleRepository).save(dueSchedule);
        assertThat(handler.commandType()).isEqualTo(SendWindowRemindersCommand.class);
    }

    @Test
    void send_alreadySentByAnotherInstance_doesNotSave() {
        SendWindowRemindersCommandHandler handler =
                new SendWindowRemindersCommandHandler(scheduleRepository, leagueRepository, windowReminderService);
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(dueSchedule));
        when(windowReminderService.sendDue(dueSchedule, dueLeague)).thenReturn(false);

        assertThat(handler.handle(new SendWindowRemindersCommand("l1"))).isFalse();
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void send_unknownLeagueOrSchedule_doesNothing() {
        SendWindowRemindersCommandHandler handler =
                new SendWindowRemindersCommandHandler(scheduleRepository, leagueRepository, windowReminderService);
        when(scheduleRepository.findByLeagueId("zz")).thenReturn(Optional.empty());

        assertThat(handler.handle(new SendWindowRemindersCommand("zz"))).isFalse();
        verify(windowReminderService, never()).sendDue(any(), any());
    }
}
