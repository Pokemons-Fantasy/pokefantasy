package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.pokemons.PokemonFacade;
import com.villu.pokefantasy.response.AvailablePokemonResponse;
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

    @GetMapping("/pokemons/available")
    public ResponseEntity<List<AvailablePokemonResponse>> getAvailablePokemons() throws Exception {
        return ResponseEntity.ok(pokemonFacade.getAvailablePokemons());
    }
}
