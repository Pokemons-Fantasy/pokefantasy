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
    private Integer tierPctS;        // % of pool assigned to S tier (default 20)
    private Integer tierPctA;        // % of pool assigned to A tier (default 20)
    private Integer tierPctB;        // % of pool assigned to B tier (default 20)
    private Integer tierPctC;        // % of pool assigned to C tier (default 20)
    private Integer tierPctD;        // % of pool assigned to D tier (default 20)
    private Integer turnTimerSeconds; // seconds per turn; null or 0 = disabled

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
                .tierPctS(20)
                .tierPctA(20)
                .tierPctB(20)
                .tierPctC(20)
                .tierPctD(20)
                .turnTimerSeconds(0)
                .build();
    }
}
