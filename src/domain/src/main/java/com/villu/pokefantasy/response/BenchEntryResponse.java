package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BenchEntryResponse {
    private int pokemonId;
    private String pokemonName;
    private String sprite;
    private String tier;    // "S" | "A" | "B" | "C" | "D"
    private Integer price;  // monedas requeridas (0 = gratis)
}
