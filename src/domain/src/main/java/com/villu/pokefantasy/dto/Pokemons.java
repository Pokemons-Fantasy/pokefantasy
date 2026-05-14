package com.villu.pokefantasy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pokemons implements Serializable {

    private Integer id;
    private String name;
    private List<Ability> abilities;
    private List<Move> moves;
    private List<Stat> stats;
    private List<Type> types;
    private Sprites sprites;
    private String leagueId;
}
