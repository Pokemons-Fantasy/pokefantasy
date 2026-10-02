package com.villu.pokefantasy.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePokemonResponse {
    private Integer id;
    private String name;
    private String spriteUrl;
    /** Tipos en inglés, en orden de slot. Null mientras la caché no los tenga. */
    private List<String> types;
}
