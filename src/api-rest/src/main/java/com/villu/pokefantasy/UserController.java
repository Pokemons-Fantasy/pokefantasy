package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.users.UserFacade;
import com.villu.pokefantasy.request.user.AddPokemonsUserRequest;
import com.villu.pokefantasy.request.user.UserRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class UserController {

    private final UserFacade userFacade;

    public UserController(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @PostMapping("/user")
    public ResponseEntity<Void> createUser(@RequestBody UserRequest user) throws Exception {
        userFacade.create(user.getUsername(), user.getPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/login")
    public ResponseEntity<String> loginUser(@RequestBody UserRequest user) throws Exception {
        return ResponseEntity.ok(userFacade.login(user.getUsername(), user.getPassword()));
    }

    @PostMapping("/user/add/pokemons")
    public ResponseEntity<Void> addPokemonsToUser(@RequestBody AddPokemonsUserRequest request) throws Exception {
        userFacade.addPokemonsToUser(request.getUserName(), request.getPokemons());
        return ResponseEntity.ok().build();
    }
}
