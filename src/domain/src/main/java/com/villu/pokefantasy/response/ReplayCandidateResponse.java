package com.villu.pokefantasy.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Un replay de Showdown que podría ser una partida del partido, con si se puede usar y por qué no. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayCandidateResponse {
    private String id;
    private String url;
    /** Epoch en segundos. */
    private long uploadtime;
    /** Usuario de la liga que ganó la partida; {@code null} si no es de los jugadores o fue empate. */
    private String winnerUsername;
    /** Serie {@code Best Of} a la que pertenece; {@code null} si es suelta. */
    private String seriesId;
    private Integer gameNumber;
    private boolean selectable;
    /** Por qué no se puede usar; {@code null} si se puede. */
    private String reason;
    /** Diferencias con las alineaciones declaradas (no impiden usarlo). */
    private List<String> warnings;
}
