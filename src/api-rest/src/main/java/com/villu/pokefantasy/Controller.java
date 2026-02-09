package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.getPokemon.GetPokemonRest;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class Controller {

    @Autowired
    private GetPokemonRest getPokemonRest;

    @GetMapping("/pokemons/{id}")
    public ResponseEntity<PokemonsResponse> getPokemons(@PathVariable int id) throws Exception {
        return ResponseEntity.ok(getPokemonRest.getPokemonById(id));
    }

}
