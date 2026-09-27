package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.draft.DraftFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Auto-pick en servidor de los turnos de draft vencidos. El cliente ya lo lanza al llegar a cero, pero
 * si nadie tiene la app abierta el draft se quedaba atascado; este job lo cubre.
 */
@Component
@Slf4j
public class DraftTurnTimeoutJob {

    private final DraftFacade draftFacade;
    private final RealtimeNotifier realtimeNotifier;

    public DraftTurnTimeoutJob(DraftFacade draftFacade, RealtimeNotifier realtimeNotifier) {
        this.draftFacade = draftFacade;
        this.realtimeNotifier = realtimeNotifier;
    }

    @Scheduled(initialDelayString = "${draft.turn-timeout-check-ms:15000}",
               fixedDelayString = "${draft.turn-timeout-check-ms:15000}")
    public void autoPickExpiredTurns() {
        List<String> leagueIds;
        try {
            leagueIds = draftFacade.expiredTurnLeagueIds();
        } catch (Exception e) {
            log.warn("Could not list expired draft turns: {}", e.getMessage());
            return;
        }
        for (String leagueId : leagueIds) {
            try {
                draftFacade.expireTurn(leagueId);
                log.info("Server auto-pick for expired draft turn in league {}", leagueId);
                realtimeNotifier.draftUpdated(leagueId);
            } catch (IllegalStateException e) {
                // Carrera normal: el jugador o un cliente hicieron el pick entre el listado y ahora.
                log.debug("Skipped auto-pick for league {}: {}", leagueId, e.getMessage());
            } catch (Exception e) {
                log.warn("Server auto-pick failed for league {}: {}", leagueId, e.getMessage());
            }
        }
    }
}
