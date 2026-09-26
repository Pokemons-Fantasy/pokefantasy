package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cada 15 min busca ligas cuya ventana de robos o de swaps cierra pronto y avisa por push a sus
 * miembros (una vez por cierre; la regla está en {@code WindowReminderService}).
 */
@Component
@Slf4j
public class WindowReminderJob {

    private final ScheduleFacade scheduleFacade;

    public WindowReminderJob(ScheduleFacade scheduleFacade) {
        this.scheduleFacade = scheduleFacade;
    }

    @Scheduled(initialDelayString = "${schedule.window-reminder-check-ms:60000}",
               fixedDelayString = "${schedule.window-reminder-check-ms:900000}")
    public void sendWindowReminders() {
        List<String> leagueIds;
        try {
            leagueIds = scheduleFacade.leaguesWithDueWindowReminders();
        } catch (Exception e) {
            log.warn("Could not list due window reminders: {}", e.getMessage());
            return;
        }
        for (String leagueId : leagueIds) {
            try {
                if (scheduleFacade.sendWindowReminders(leagueId)) {
                    log.info("Sent window closing reminder for league {}", leagueId);
                }
            } catch (Exception e) {
                log.warn("Window reminder failed for league {}: {}", leagueId, e.getMessage());
            }
        }
    }
}
