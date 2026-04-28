package com.villu.pokefantasy.commands.pokemons.saved;

import com.villu.pokefantasy.dto.Ability;
import com.villu.pokefantasy.dto.Move;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Type;
import lombok.Data;

import java.util.List;

@Data
public class GetSavedPokemonsCommandResponse {

    private Integer id;
    private String name;
    private List<Ability> abilities;
    private List<Move> moves;
    private List<Stat> stats;
    private List<Type> types;

}
