package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.avatar.DeleteAvatarCommand;
import com.villu.pokefantasy.commands.users.avatar.GetAvatarCommand;
import com.villu.pokefantasy.commands.users.avatar.UploadAvatarCommand;
import com.villu.pokefantasy.commands.users.create.CreateUserCommand;
import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.commands.users.login.LoginUserCommand;
import com.villu.pokefantasy.commands.users.logout.LogoutUserCommand;
import com.villu.pokefantasy.commands.users.me.GetCurrentUserCommand;
import com.villu.pokefantasy.commands.users.password.ChangePasswordCommand;
import com.villu.pokefantasy.commands.users.pushtoken.RegisterPushTokenCommand;
import com.villu.pokefantasy.commands.users.pushtoken.UnregisterPushTokenCommand;
import com.villu.pokefantasy.commands.users.search.SearchUsersCommand;
import com.villu.pokefantasy.mediator.Mediator;
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

    private final Mediator mediator;

    public UserFacade(Mediator mediator) {
        this.mediator = mediator;
    }


    public void create(String username, String password) throws Exception {
        mediator.send(new CreateUserCommand(username, password));
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

