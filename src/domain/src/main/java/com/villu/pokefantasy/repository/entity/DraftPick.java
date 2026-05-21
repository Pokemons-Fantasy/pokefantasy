package com.villu.pokefantasy.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DraftPick {
    private String username;
    private String pokemonName;
    private Integer pokemonId;
    private int round;
    private Instant pickedAt;
    /** Custom steal price set by the owner spending coins. null = use priceTierX default. Inherited when stolen. Reset to null when returned to bench. */
    private Integer customStealPrice;
    /** Round number of the jornada in which this pokemon was stolen. null = not locked. Locked until that jornada is fully COMPLETED. */
    private Integer lockedUntilRound;
}
