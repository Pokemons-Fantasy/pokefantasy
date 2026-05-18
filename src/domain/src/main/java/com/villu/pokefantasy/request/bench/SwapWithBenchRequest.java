package com.villu.pokefantasy.request.bench;

import lombok.Data;

@Data
public class SwapWithBenchRequest {
    private String pokemonToGive;
    private String pokemonToTake;
}
