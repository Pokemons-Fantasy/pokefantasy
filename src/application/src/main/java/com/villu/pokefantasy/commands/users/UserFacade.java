package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.avatar.DeleteAvatarCommand;
import com.villu.pokefantasy.commands.users.avatar.GetAvatarCommand;
import com.villu.pokefantasy.commands.users.avatar.UploadAvatarCommand;
import com.villu.pokefantasy.commands.users.create.CreateUserCommand;
import com.villu.pokefantasy.commands.users.delete.DeleteAccountCommand;
import com.villu.pokefantasy.commands.users.delete.RevokeSessionsCommand;
import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.commands.users.login.LoginUserCommand;
import com.villu.pokefantasy.commands.users.logout.LogoutUserCommand;
import com.villu.pokefantasy.commands.users.me.GetCurrentUserCommand;
import com.villu.pokefantasy.commands.users.password.ChangePasswordCommand;
import com.villu.pokefantasy.commands.users.pushtoken.RegisterPushTokenCommand;
import com.villu.pokefantasy.commands.users.pushtoken.UnregisterPushTokenCommand;
import com.villu.pokefantasy.commands.users.search.SearchUsersCommand;
import com.villu.pokefantasy.commands.users.showdown.LinkShowdownCommand;
import com.villu.pokefantasy.commands.users.showdown.ShowdownIds;
import com.villu.pokefantasy.commands.users.showdown.UnlinkShowdownCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.ports.ShowdownPort;
import com.villu.pokefantasy.ports.ShowdownUser;
import com.villu.pokefantasy.response.AvatarImageResponse;
import com.villu.pokefantasy.response.CurrentUserResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Fachada para mantener la "lógica" agrupada como estaba (antes en SaveUser)
 * pero delegando en CommandHandlers.
 */
@Service
public class UserFacade {

    /** Máximo de caracteres de un nombre de Showdown. */
    static final int SHOWDOWN_NAME_MAX = 18;

    private final Mediator mediator;
    private final ShowdownPort showdownPort;

    public UserFacade(Mediator mediator, ShowdownPort showdownPort) {
        this.mediator = mediator;
        this.showdownPort = showdownPort;
    }

    /**
     * Vincula la cuenta de Showdown {@code rawName}. La consulta a Showdown se hace aquí, antes del comando
     * (fuera de la transacción, ADR-007), y el comando recibe el nombre canónico que devuelve Showdown.
     */
    public void linkShowdown(String username, String rawName) throws Exception {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Indica tu nombre de Showdown.");
        }
        if (name.length() > SHOWDOWN_NAME_MAX) {
            throw new IllegalArgumentException(
                    "El nombre de Showdown no puede tener más de " + SHOWDOWN_NAME_MAX + " caracteres.");
        }
        ShowdownUser account = showdownPort.findUser(ShowdownIds.toId(name))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una cuenta registrada en Showdown con el nombre '" + name + "'."));
        mediator.send(new LinkShowdownCommand(username, account.username(), account.userid()));
    }

    public void unlinkShowdown(String username) throws Exception {
        mediator.send(new UnlinkShowdownCommand(username));
    }


    public void create(String username, String password, String clientIp) throws Exception {
        mediator.send(new CreateUserCommand(username, password, clientIp));
    }

    public LoginResult login(String username, String password, String clientIp) throws Exception {
        return mediator.send(new LoginUserCommand(username, password, clientIp));
    }

    public void logout(String refreshToken) throws Exception {
        mediator.send(new LogoutUserCommand(refreshToken));
    }

    public LoginResult changePassword(String username, String currentPassword, String newPassword) throws Exception {
        return mediator.send(new ChangePasswordCommand(username, currentPassword, newPassword));
    }

    public List<String> searchUsers(String prefix, String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new SearchUsersCommand(prefix, leagueId, requestingUsername));
    }

    /** Borra la cuenta. Devuelve las ligas que siguen existiendo, para avisar a sus miembros. */
    public List<String> deleteAccount(String username, String password) throws Exception {
        return mediator.send(new DeleteAccountCommand(username, password));
    }

    /** Tras borrar la cuenta, ya con la transacción confirmada: ver {@code RevokeSessionsCommandHandler}. */
    public void revokeSessions(String username) throws Exception {
        mediator.send(new RevokeSessionsCommand(username));
    }

    public void registerPushToken(String username, String token) throws Exception {
        mediator.send(new RegisterPushTokenCommand(username, token));
    }

    public void unregisterPushToken(String username, String token) throws Exception {
        mediator.send(new UnregisterPushTokenCommand(username, token));
    }

    /** Guarda la foto de perfil (ya recortada por el cliente) y devuelve su versión. */
    public long uploadAvatar(String username, byte[] image) throws Exception {
        return mediator.send(new UploadAvatarCommand(username, image));
    }

    public void deleteAvatar(String username) throws Exception {
        mediator.send(new DeleteAvatarCommand(username));
    }

    public Optional<AvatarImageResponse> getAvatar(String username, String requestingUsername) throws Exception {
        return mediator.send(new GetAvatarCommand(username, requestingUsername));
    }

    public CurrentUserResponse me(String username) throws Exception {
        return mediator.send(new GetCurrentUserCommand(username));
    }
}

