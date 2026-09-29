package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.ActivityEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEventResponse {
    private String id;
    private String leagueId;
    private ActivityEventType type;
    private String actorUsername;
    private String targetUsername;
    private String pokemonName;
    private String pokemonName2;
    private Integer coinsAmount;
    /** STEAL: monedas que cobró la víctima. Null en robos antiguos (cobraba 2 × {@code coinsAmount}). */
    private Integer targetCoinsAmount;
    private String fromTier;
    private String toTier;
    private Integer roundNumber;
    private Instant createdAt;
}
