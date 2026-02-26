package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.users.PokemonFacade;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class PkmnController {

    private final PokemonFacade pokemonFacade;

    public PkmnController(PokemonFacade pokemonFacade) {
        this.pokemonFacade = pokemonFacade;
    }


    @GetMapping("/pokemons/{id}")
    public ResponseEntity<PokemonsResponse> getPokemons(@PathVariable int id) throws Exception {
        return ResponseEntity.ok(pokemonFacade.getPokemon(id));
    }

    @PostMapping("/pokemons/add")
    public ResponseEntity<Void> addPokemon(@RequestBody List<String> pokemonsName) throws Exception {
        pokemonFacade.addPokemons(pokemonsName);
        return ResponseEntity.ok().build();
    }




}
