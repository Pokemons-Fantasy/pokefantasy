package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "schedule")
public class ScheduleEntity {

    @Id
    private String id;

    private String leagueId;

    private List<Jornada> jornadas;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Jornada {
        private int roundNumber;
        private List<Match> matches;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Match {
        private String id;
        private String player1;
        private String player2;
        private String winnerUsername;
        private MatchStatus status;
    }
}
