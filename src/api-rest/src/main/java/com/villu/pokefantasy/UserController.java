package com.villu.pokefantasy;

import com.villu.pokefantasy.auth.AuthCookies;
import com.villu.pokefantasy.commands.users.UserFacade;
import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.request.user.ChangePasswordRequest;
import com.villu.pokefantasy.request.user.RegisterPushTokenRequest;
import com.villu.pokefantasy.request.user.UserRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) throws Exception {
        LoginResult login = userFacade.login(user.getUsername(), user.getPassword(), clientIp(request));
        setSessionCookies(response, login);
        return ResponseEntity.ok(new LoginResponse(user.getUsername()));
    }

    /**
     * Cambia la contraseña. Cierra todas las sesiones del usuario (también en otros dispositivos) y
     * devuelve cookies nuevas para esta, así que quien la cambia sigue dentro.
     */
    @PutMapping("/user/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody ChangePasswordRequest body,
                                               HttpServletResponse response) throws Exception {
        LoginResult session = userFacade.changePassword(
                userDetails.getUsername(), body.getCurrentPassword(), body.getNewPassword());
        setSessionCookies(response, session);
        return ResponseEntity.noContent().build();
    }

    private static void setSessionCookies(HttpServletResponse response, LoginResult session) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                AuthCookies.set(AuthCookies.ACCESS, session.accessToken(), session.accessTokenTtl()));
        if (session.refreshToken() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    AuthCookies.set(AuthCookies.REFRESH, session.refreshToken(), session.refreshTokenTtl()));
        }
    }

    /**
     * IP real del cliente. En Render el tráfico llega por Cloudflare y su balanceador, que ponen la IP
     * del cliente como primera entrada de {@code X-Forwarded-For}; sin cabecera (local) se usa la del socket.
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/user/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
            HttpServletResponse response) throws Exception {
        userFacade.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookies.clear(AuthCookies.ACCESS));
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookies.clear(AuthCookies.REFRESH));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<String>> searchUsers(
            @RequestParam String q,
            @RequestParam(required = false) String leagueId,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(userFacade.searchUsers(q, leagueId, userDetails.getUsername()));
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
