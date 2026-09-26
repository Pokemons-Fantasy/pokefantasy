package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WindowReminderJobTest {

    @Mock private ScheduleFacade scheduleFacade;

    private WindowReminderJob job;

    @BeforeEach
    void setUp() {
        job = new WindowReminderJob(scheduleFacade);
    }

    @Test
    void sendsRemindersForEveryDueLeague_evenIfOneFails() throws Exception {
        when(scheduleFacade.leaguesWithDueWindowReminders()).thenReturn(List.of("broken", "l1", "raced"));
        when(scheduleFacade.sendWindowReminders("broken")).thenThrow(new RuntimeException("mongo down"));
        when(scheduleFacade.sendWindowReminders("l1")).thenReturn(true);
        when(scheduleFacade.sendWindowReminders("raced")).thenReturn(false);

        job.sendWindowReminders();

        verify(scheduleFacade).sendWindowReminders("l1");
        verify(scheduleFacade).sendWindowReminders("raced");
    }

    @Test
    void listingFails_doesNothing() throws Exception {
        when(scheduleFacade.leaguesWithDueWindowReminders()).thenThrow(new RuntimeException("mongo down"));

        job.sendWindowReminders();

        verify(scheduleFacade, never()).sendWindowReminders(anyString());
    }
}
