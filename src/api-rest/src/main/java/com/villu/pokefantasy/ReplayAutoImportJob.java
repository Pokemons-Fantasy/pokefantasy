package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import com.villu.pokefantasy.commands.schedule.replay.AutoImportTarget;
import com.villu.pokefantasy.ports.ShowdownReplay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registra solo los resultados cuyos replays ya están subidos a Showdown: cada pasada mira los partidos
 * pendientes con el mercado cerrado y, si hay una serie sin ambigüedad, la importa como el import manual. Un
 * partido que falla (Showdown caído, ya registrado a mano) se apunta y se sigue con el siguiente.
 */
@Component
@Slf4j
public class ReplayAutoImportJob {

    private final ScheduleFacade scheduleFacade;

    public ReplayAutoImportJob(ScheduleFacade scheduleFacade) {
        this.scheduleFacade = scheduleFacade;
    }

    @Scheduled(initialDelayString = "${schedule.replay-auto-import-ms:120000}",
               fixedDelayString = "${schedule.replay-auto-import-ms:1200000}")
    public void importReplays() {
        List<AutoImportTarget> targets;
        try {
            targets = scheduleFacade.autoImportTargets();
        } catch (Exception e) {
            log.warn("Could not list auto-import targets: {}", e.getMessage());
            return;
        }
        for (AutoImportTarget target : targets) {
            try {
                Optional<List<ShowdownReplay>> series = scheduleFacade.pickAutoImport(target);
                if (series.isPresent()) {
                    scheduleFacade.importSeriesAsSystem(target, series.get());
                    log.info("Auto-imported result of match {} in league {}", target.matchId(), target.leagueId());
                }
            } catch (Exception e) {
                // Los mensajes de error ya llevan los ids de replay sin contraseña (ReplayCandidates.redact).
                log.warn("Auto-import failed for match {} in league {}: {}", target.matchId(), target.leagueId(),
                        e.getMessage());
            }
        }
    }
}
