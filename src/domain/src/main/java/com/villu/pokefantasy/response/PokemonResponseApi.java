package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.ResultPokemonDto;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PokemonResponseApi implements Serializable {

    List<ResultPokemonDto> results;
}
