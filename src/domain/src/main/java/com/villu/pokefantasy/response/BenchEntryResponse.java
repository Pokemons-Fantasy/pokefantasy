package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BenchEntryResponse {
    private int pokemonId;
    private String pokemonName;
    private String sprite;
}
