package com.villu.pokefantasy.request.user;


import com.villu.pokefantasy.dto.Pokemons;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class AddPokemonsUserRequest {

    private String userName;
    private List<Pokemons> pokemons;
}
