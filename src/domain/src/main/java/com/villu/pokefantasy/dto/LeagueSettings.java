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

    /** Day of week (ISO: 1=Mon … 7=Sun) when the steal window closes. Default 4 (Thursday). */
    private Integer stealWindowCloseDay;
    /** Time of day when the steal window closes, format "HH:mm". Default "23:59". */
    private String stealWindowCloseTime;
    /** Day of week (ISO: 1=Mon … 7=Sun) when the swap window closes. Default 5 (Friday). */
    private Integer swapWindowCloseDay;
    /** Time of day when the swap window closes, format "HH:mm". Default "16:00". */
    private String swapWindowCloseTime;

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
                .stealWindowCloseDay(4)
                .stealWindowCloseTime("23:59")
                .swapWindowCloseDay(5)
                .swapWindowCloseTime("16:00")
                .build();
    }
}
