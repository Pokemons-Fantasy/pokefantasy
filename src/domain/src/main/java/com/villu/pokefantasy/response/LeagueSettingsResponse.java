package com.villu.pokefantasy.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeagueSettingsResponse {
    private Integer coinsPerWin;
    private Integer coinsPerLoss;
    private Integer priceTierS;
    private Integer priceTierA;
    private Integer priceTierB;
    private Integer priceTierC;
    private Integer priceTierD;
}
