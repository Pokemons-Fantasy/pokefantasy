package com.villu.pokefantasy.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ResultPokemonDto implements Serializable {

    private String name;
    private String url;
    private Integer id;
}
