package com.villu.pokefantasy.request.league;

import lombok.Data;

@Data
public class UpdateLeagueSettingsRequest {
    private Integer coinsPerWin;
    private Integer coinsPerLoss;
    private Integer priceTierS;
    private Integer priceTierA;
    private Integer priceTierB;
    private Integer priceTierC;
    private Integer priceTierD;
    private String seasonStartDate;  // ISO "YYYY-MM-DD", optional
    private Integer maxTeamSize;     // optional, default 20
    private Integer tierPctS;        // % of pool for S tier (default 20)
    private Integer tierPctA;        // % of pool for A tier (default 20)
    private Integer tierPctB;        // % of pool for B tier (default 20)
    private Integer tierPctC;        // % of pool for C tier (default 20)
    private Integer tierPctD;        // % of pool for D tier (default 20)
}
