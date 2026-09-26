package com.villu.pokefantasy.request.schedule;

import lombok.Data;

@Data
public class RecordMatchResultRequest {
    private String winnerUsername;
    /** Marcador opcional (p. ej. 3 y 1): los dos o ninguno, y el ganador con más. */
    private Integer winnerScore;
    private Integer loserScore;
}
