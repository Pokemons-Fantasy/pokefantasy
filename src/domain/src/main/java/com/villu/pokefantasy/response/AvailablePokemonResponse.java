package com.villu.pokefantasy.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePokemonResponse {
    private Integer id;
    private String name;
    private String spriteUrl;
}
