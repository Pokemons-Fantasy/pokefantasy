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
        /** Desde cuándo se puede declarar la alineación (cierre del mercado); {@code null} sin fecha. */
        private String lineupOpensAt;
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
        /** Marcador desde el punto de vista del ganador; {@code null} si no se indicó. */
        private Integer winnerScore;
        private Integer loserScore;
        /** Si cada jugador ha enviado ya su alineación (lo ve todo el mundo). */
        private boolean player1LineupSubmitted;
        private boolean player2LineupSubmitted;
        /** La alineación de cada lado: solo la propia hasta que estén las dos; después, todo el mundo. */
        private LineupResponse player1Lineup;
        private LineupResponse player2Lineup;
        /** Quien pide puede declarar (o cambiar) su alineación ahora mismo. */
        private boolean lineupOpen;
        /** Quien pide podrá declarar su alineación cuando cierre el mercado ({@code lineupOpensAt} de la jornada). */
        private boolean lineupPending;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineupResponse {
        private List<String> pokemonNames;
        /** Enlace al equipo completo (lista abierta). */
        private String pasteUrl;
        /** Diferencias entre el paste y la alineación. */
        private List<String> pasteWarnings;
    }
}
