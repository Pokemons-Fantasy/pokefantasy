package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.admin.AdminFacade;
import com.villu.pokefantasy.commands.users.UserFacade;
import com.villu.pokefantasy.request.admin.RenameLeagueRequest;
import com.villu.pokefantasy.response.AdminLeagueResponse;
import com.villu.pokefantasy.response.AdminUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Moderación (L5). Solo el rol global ADMIN: lo exige SecurityConfig para todo /v1/admin/**. */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AdminFacade adminFacade;
    private final UserFacade userFacade;
    private final UserSseEmitterRegistry userSseRegistry;
    private final RealtimeNotifier realtimeNotifier;

    public AdminController(AdminFacade adminFacade, UserFacade userFacade, UserSseEmitterRegistry userSseRegistry,
                           RealtimeNotifier realtimeNotifier) {
        this.adminFacade = adminFacade;
        this.userFacade = userFacade;
        this.userSseRegistry = userSseRegistry;
        this.realtimeNotifier = realtimeNotifier;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> searchUsers(@RequestParam String q) throws Exception {
        return ResponseEntity.ok(adminFacade.searchUsers(q));
    }

    @GetMapping("/leagues")
    public ResponseEntity<List<AdminLeagueResponse>> searchLeagues(@RequestParam String q) throws Exception {
        return ResponseEntity.ok(adminFacade.searchLeagues(q));
    }

    @DeleteMapping("/users/{username}/avatar")
    public ResponseEntity<Void> removeAvatar(@AuthenticationPrincipal UserDetails admin,
                                             @PathVariable String username) throws Exception {
        adminFacade.removeAvatar(admin.getUsername(), username);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/leagues/{leagueId}/name")
    public ResponseEntity<Void> renameLeague(@AuthenticationPrincipal UserDetails admin,
                                             @PathVariable String leagueId,
                                             @RequestBody RenameLeagueRequest body) throws Exception {
        adminFacade.renameLeague(admin.getUsername(), leagueId, body.getName());
        return ResponseEntity.noContent().build();
    }

    /** Como el borrado propio: tras el commit corta sesiones y SSE del usuario y avisa a sus ligas. */
    @DeleteMapping("/users/{username}")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal UserDetails admin,
                                           @PathVariable String username) throws Exception {
        List<String> leagues = adminFacade.deleteUser(admin.getUsername(), username);
        userFacade.revokeSessions(username);
        userSseRegistry.closeAll(username);
        leagues.forEach(realtimeNotifier::draftUpdated);
        return ResponseEntity.noContent().build();
    }
}
