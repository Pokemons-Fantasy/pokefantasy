package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DraftStatusResponse {
    private String id;
    private DraftStatus status;
    private List<String> turnOrder;
    private String currentTurn;
    private int currentRound;
    private List<DraftPickResponse> picks;
    /** Draft original (lo que eligió cada jugador), inmune a robos/swaps/trades. */
    private List<DraftPickResponse> draftHistory;
    /** Deadline for the current turn; null if timer is disabled or draft not in progress. */
    private Instant turnDeadline;
    /** Presupuesto, precios y snake. null en drafts anteriores a la configuración. */
    private DraftConfig config;
    /** Monedas que le quedan a cada jugador del orden de turnos. null si el draft no tiene presupuesto. */
    private Map<String, Integer> budgets;
}
