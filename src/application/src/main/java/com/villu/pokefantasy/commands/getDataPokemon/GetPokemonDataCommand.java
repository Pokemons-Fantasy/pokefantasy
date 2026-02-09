package com.villu.pokefantasy.commands.getDataPokemon;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetPokemonDataCommand {

    private String url;
    private String name;
}
