package com.villu.pokefantasy.initializePkmn;

import com.villu.pokefantasy.dto.ResultPokemonDto;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class GetPokemonsResponse implements Serializable {

    List<ResultPokemonDto> results;
}
