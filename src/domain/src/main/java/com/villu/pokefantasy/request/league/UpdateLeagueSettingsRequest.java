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
}
