package com.villu.pokefantasy.request.league;

import lombok.Data;

@Data
public class UpdateLeagueSettingsRequest {
    private Integer coinsPerWin;
    private Integer coinsPerLoss;
}
