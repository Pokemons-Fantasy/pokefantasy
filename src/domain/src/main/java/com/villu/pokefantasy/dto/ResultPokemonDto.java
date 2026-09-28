package com.villu.pokefantasy.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ResultPokemonDto implements Serializable {

    private String name;
    private String url;
    private Integer id;
    /** No viene en la lista de PokeAPI: lo rellena {@code PokemonCacheLoader} con las listas por tipo. */
    private List<String> types;
}
