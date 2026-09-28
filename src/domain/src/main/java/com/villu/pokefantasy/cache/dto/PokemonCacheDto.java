package com.villu.pokefantasy.cache.dto;

import lombok.Data;
import lombok.Value;
import lombok.With;

import java.util.List;

@Value
@Data
public class PokemonCacheDto {

    String url;
    String name;
    Integer id;
    /** Tipos en inglés (como PokeAPI), en orden de slot. Null en cachés anteriores a guardarlos. */
    @With
    List<String> types;
}
