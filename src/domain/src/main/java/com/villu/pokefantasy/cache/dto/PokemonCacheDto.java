package com.villu.pokefantasy.cache.dto;

import lombok.Data;
import lombok.Value;

@Value
@Data
public class PokemonCacheDto {

    String url;
    String name;
    Integer id;
}
