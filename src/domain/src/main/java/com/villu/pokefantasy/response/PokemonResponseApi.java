package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.ResultPokemonDto;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PokemonResponseApi implements Serializable {

    //Este es el que se obtiene de llamar a la api de pokemon
    List<ResultPokemonDto> results;
}
