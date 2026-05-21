package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.Tier;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TierChangeDto {
    private int    pokemonId;
    private String pokemonName;
    private Tier   oldTier;
    private Tier   newTier;
}
