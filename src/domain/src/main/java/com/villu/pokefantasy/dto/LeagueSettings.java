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

    public static LeagueSettings defaults() {
        return LeagueSettings.builder()
                .coinsPerWin(100)
                .coinsPerLoss(50)
                .build();
    }
}
