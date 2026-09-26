package com.villu.pokefantasy.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStandingResponse {
    private String username;
    private int played;
    private int wins;
    private int losses;
    private int coins;
    /** Suma de marcadores a favor/en contra (solo de partidos con marcador) y su diferencia. */
    private int scoreFor;
    private int scoreAgainst;
    private int scoreDiff;
}
