package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.Ability;
import com.villu.pokefantasy.dto.Move;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Type;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PokemonsResponse {
    //Este es el objeto que devuelvo yo
    private Integer id;
    private String name;
    private List<Ability> abilities;
    private List<Move> moves;
    private List<Stat> stats;
    private List<Type> types;
    private String sprite;

}
