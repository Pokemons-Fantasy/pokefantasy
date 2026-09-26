package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.MatchStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "schedule")
public class ScheduleEntity {

    @Id
    private String id;
    @Version
    private Long version;

    @Indexed
    private String leagueId;

    private List<Jornada> jornadas;

    @Data
    @NoArgsConstructor
    public static class Jornada {
        private int roundNumber;
        private List<Match> matches;
        private String startDate;   // ISO "YYYY-MM-DD", null until admin sets seasonStartDate
        /**
         * Cierre de la ventana de robos/swaps (ISO local, hora de {@code LEAGUE_ZONE}) del que ya se avisó
         * por push, para no repetir el aviso. Si el admin cambia la hora de cierre, se vuelve a avisar.
         */
        private String stealReminderSentFor;
        private String swapReminderSentFor;

        public Jornada(int roundNumber, List<Match> matches, String startDate) {
            this.roundNumber = roundNumber;
            this.matches = matches;
            this.startDate = startDate;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Match {
        private String id;
        private String player1;
        private String player2;
        private String winnerUsername;
        private MatchStatus status;
        /**
         * Monedas que se dieron al registrar el resultado, para poder devolver exactamente esas al
         * corregirlo o deshacerlo aunque los ajustes de la liga hayan cambiado. {@code null} en
         * resultados registrados antes de existir el campo.
         */
        private Integer winnerCoins;
        private Integer loserCoins;
        /** Marcador (p. ej. 3–1) desde el punto de vista del ganador; {@code null} si no se indicó. */
        private Integer winnerScore;
        private Integer loserScore;

        public Match(String id, String player1, String player2, String winnerUsername, MatchStatus status) {
            this.id = id;
            this.player1 = player1;
            this.player2 = player2;
            this.winnerUsername = winnerUsername;
            this.status = status;
        }
    }
}
