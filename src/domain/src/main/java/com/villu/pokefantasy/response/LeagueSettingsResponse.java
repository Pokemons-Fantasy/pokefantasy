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
}
