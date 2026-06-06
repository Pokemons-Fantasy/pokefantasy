package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.users.UserFacade;
import com.villu.pokefantasy.request.user.AddPokemonsUserRequest;
import com.villu.pokefantasy.request.user.RegisterPushTokenRequest;
import com.villu.pokefantasy.request.user.UserRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

    public UserController(UserFacade userFacade,
                          UserSseEmitterRegistry userSseRegistry) {
        this.userFacade = userFacade;
        this.userSseRegistry = userSseRegistry;
    }

    @PostMapping("/user")
    public ResponseEntity<Void> createUser(@RequestBody UserRequest user) throws Exception {
        userFacade.create(user.getUsername(), user.getPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/login")
    public ResponseEntity<LoginResponse> loginUser(@RequestBody UserRequest user,
                                                   HttpServletResponse response) throws Exception {
        String token = userFacade.login(user.getUsername(), user.getPassword());
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(86400)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(new LoginResponse(user.getUsername()));
    }

    @PostMapping("/user/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie clear = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());
        return ResponseEntity.ok().build();
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
    public SseEmitter streamUserEvents(@AuthenticationPrincipal UserDetails userDetails) {
        return userSseRegistry.register(userDetails.getUsername());
    }
}
