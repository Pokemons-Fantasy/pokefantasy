package com.villu.pokefantasy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeagueSettings {

    private Integer coinsPerWin;
    private Integer coinsPerLoss;
    private Integer priceTierS;
    private Integer priceTierA;
    private Integer priceTierB;
    private Integer priceTierC;
    private Integer priceTierD;
    private String seasonStartDate;  // ISO "YYYY-MM-DD", null until admin sets it
    private Integer maxTeamSize;     // post-draft team cap (default 20)

    public static LeagueSettings defaults() {
        return LeagueSettings.builder()
                .coinsPerWin(100)
                .coinsPerLoss(50)
                .priceTierS(0)
                .priceTierA(0)
                .priceTierB(0)
                .priceTierC(0)
                .priceTierD(0)
                .seasonStartDate(null)
                .maxTeamSize(20)
                .build();
    }
}
