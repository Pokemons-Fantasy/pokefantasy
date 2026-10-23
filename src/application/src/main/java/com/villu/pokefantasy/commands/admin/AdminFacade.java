package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.commands.users.avatar.DeleteAvatarCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.AdminLeagueResponse;
import com.villu.pokefantasy.response.AdminUserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** Moderación (L5): acciones del admin global. El rol lo comprueba SecurityConfig (/v1/admin/**). */
@Service
@Slf4j
public class AdminFacade {

    private final Mediator mediator;

    public AdminFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public List<AdminUserResponse> searchUsers(String q) throws Exception {
        return mediator.send(new SearchUsersForAdminCommand(q));
    }

    public List<AdminLeagueResponse> searchLeagues(String q) throws Exception {
        return mediator.send(new SearchLeaguesForAdminCommand(q));
    }

    /** Reutiliza el borrado de foto del propio usuario; el log va aquí porque ese handler es compartido. */
    public void removeAvatar(String adminUsername, String targetUsername) throws Exception {
        log.info("Admin {} removed the photo of {}", adminUsername, targetUsername);
        mediator.send(new DeleteAvatarCommand(targetUsername));
    }

    public void renameLeague(String adminUsername, String leagueId, String name) throws Exception {
        mediator.send(new RenameLeagueCommand(adminUsername, leagueId, name));
    }

    /** Devuelve las ligas que siguen existiendo, para avisar a sus miembros. */
    public List<String> deleteUser(String adminUsername, String targetUsername) throws Exception {
        return mediator.send(new AdminDeleteUserCommand(adminUsername, targetUsername));
    }
}
