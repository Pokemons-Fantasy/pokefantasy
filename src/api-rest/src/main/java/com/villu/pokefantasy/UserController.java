package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.users.UserFacade;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.request.user.AddPokemonsUserRequest;
import com.villu.pokefantasy.request.user.RegisterPushTokenRequest;
import com.villu.pokefantasy.request.user.UserRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class UserController {

    private final UserFacade userFacade;
    private final UserSseEmitterRegistry userSseRegistry;
    private final TokenPort tokenPort;

    public UserController(UserFacade userFacade,
                          UserSseEmitterRegistry userSseRegistry,
                          TokenPort tokenPort) {
        this.userFacade = userFacade;
        this.userSseRegistry = userSseRegistry;
        this.tokenPort = tokenPort;
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

    @GetMapping("/users/search")
    public ResponseEntity<List<String>> searchUsers(
            @RequestParam String q,
            @RequestParam(required = false) String leagueId) throws Exception {
        return ResponseEntity.ok(userFacade.searchUsers(q, leagueId));
    }

    @PostMapping("/users/push-token")
    public ResponseEntity<Void> registerPushToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody RegisterPushTokenRequest request) throws Exception {
        userFacade.registerPushToken(userDetails.getUsername(), request.getToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/events")
    public SseEmitter streamUserEvents(@RequestParam String token) {
        String username = tokenPort.extractUsername(token);
        if (username == null || !tokenPort.isTokenValid(token, username)) {
            throw new IllegalArgumentException("Token inválido");
        }
        return userSseRegistry.register(username);
    }
}
