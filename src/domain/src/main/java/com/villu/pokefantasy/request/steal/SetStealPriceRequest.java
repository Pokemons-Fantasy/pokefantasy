package com.villu.pokefantasy.request.steal;

import lombok.Data;

@Data
public class SetStealPriceRequest {
    private String pokemonName;
    private int newPrice;
}
