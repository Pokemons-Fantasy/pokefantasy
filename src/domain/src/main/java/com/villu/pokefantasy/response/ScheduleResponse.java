package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {

    private String leagueId;
    private List<JornadaResponse> jornadas;
    /** Estado de las ventanas calculado por el backend (única fuente de verdad). */
    private boolean stealWindowOpen;
    private boolean swapWindowOpen;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JornadaResponse {
        private int roundNumber;
        private List<MatchResponse> matches;
        private String startDate;      // "YYYY-MM-DD", null if not configured
        private String stealDeadline;  // "YYYY-MM-DDTHH:mm:ss", null if no startDate
        private String swapDeadline;   // "YYYY-MM-DDTHH:mm:ss", null if no startDate
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResponse {
        private String id;
        private String player1;
        private String player2;
        private String winnerUsername;
        private MatchStatus status;
    }
}
