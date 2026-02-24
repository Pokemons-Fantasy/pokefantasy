package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.getPokemon.GetPokemonRest;
import com.villu.pokefantasy.commands.users.saveUser.SaveUser;
import com.villu.pokefantasy.request.user.UserRequest;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class Controller {

    @Autowired
    private GetPokemonRest getPokemonRest;

    @Autowired
    private SaveUser saveUser;

    @GetMapping("/pokemons/{id}")
    public ResponseEntity<PokemonsResponse> getPokemons(@PathVariable int id) throws Exception {
        return ResponseEntity.ok(getPokemonRest.getPokemonById(id));
    }

    @PostMapping("/user")
    public ResponseEntity<Void> createUser(@RequestBody UserRequest user) {
        saveUser.saveUser(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/login")
    public ResponseEntity<Boolean> loginUser(@RequestBody UserRequest user) {
        return ResponseEntity.ok(saveUser.login(user));
    }
}
