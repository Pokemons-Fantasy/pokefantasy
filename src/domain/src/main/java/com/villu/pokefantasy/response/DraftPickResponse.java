package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DraftPickResponse {
    private String username;
    private String pokemonName;
    private Integer pokemonId;
    private int round;
    private Instant pickedAt;
    /** Custom steal price set by owner. null = use priceTierX default. */
    private Integer customStealPrice;
    /** Timestamp hasta el que este pokémon está bloqueado. null = libre. */
    private Instant lockedUntil;
}
