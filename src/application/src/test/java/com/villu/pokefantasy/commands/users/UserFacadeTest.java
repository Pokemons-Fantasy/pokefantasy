package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.avatar.DeleteAvatarCommand;
import com.villu.pokefantasy.commands.users.avatar.GetAvatarCommand;
import com.villu.pokefantasy.commands.users.avatar.UploadAvatarCommand;
import com.villu.pokefantasy.commands.users.create.CreateUserCommand;
import com.villu.pokefantasy.commands.users.delete.DeleteAccountCommand;
import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.commands.users.login.LoginUserCommand;
import com.villu.pokefantasy.commands.users.logout.LogoutUserCommand;
import com.villu.pokefantasy.commands.users.me.GetCurrentUserCommand;
import com.villu.pokefantasy.commands.users.password.ChangePasswordCommand;
import com.villu.pokefantasy.commands.users.pushtoken.RegisterPushTokenCommand;
import com.villu.pokefantasy.commands.users.pushtoken.UnregisterPushTokenCommand;
import com.villu.pokefantasy.commands.users.search.SearchUsersCommand;
import com.villu.pokefantasy.commands.users.showdown.LinkShowdownCommand;
import com.villu.pokefantasy.commands.users.showdown.UnlinkShowdownCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.ports.ShowdownPort;
import com.villu.pokefantasy.ports.ShowdownUser;
import com.villu.pokefantasy.response.AvatarImageResponse;
import com.villu.pokefantasy.response.CurrentUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFacadeTest {

    @Mock private Mediator mediator;
    @Mock private ShowdownPort showdownPort;

    private UserFacade facade;

    @BeforeEach
    void setUp() {
        facade = new UserFacade(mediator, showdownPort);
    }

    @Test
    void linkShowdown_lookupThenSend() throws Exception {
        when(showdownPort.findUser("ashketchum")).thenReturn(Optional.of(new ShowdownUser("Ash Ketchum", "ashketchum")));

        facade.linkShowdown("ash", "  Ash Ketchum! ");

        verify(mediator).send(new LinkShowdownCommand("ash", "Ash Ketchum", "ashketchum"));
    }

    @Test
    void linkShowdown_blank_throwsWithoutLookup() {
        assertThatThrownBy(() -> facade.linkShowdown("ash", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Indica tu nombre de Showdown.");
        verifyNoInteractions(showdownPort, mediator);
    }

    @Test
    void linkShowdown_tooLong_throws() {
        assertThatThrownBy(() -> facade.linkShowdown("ash", "a".repeat(19)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre de Showdown no puede tener más de 18 caracteres.");
        verifyNoInteractions(showdownPort, mediator);
    }

    @Test
    void linkShowdown_notRegistered_throws() {
        when(showdownPort.findUser("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.linkShowdown("ash", "Ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No existe una cuenta registrada en Showdown con el nombre 'Ghost'.");
        verifyNoInteractions(mediator);
    }

    @Test
    void unlinkShowdown_sendsCommand() throws Exception {
        facade.unlinkShowdown("ash");

        verify(mediator).send(new UnlinkShowdownCommand("ash"));
    }

    @Test
    void create_sendsCreateUserCommand() throws Exception {
        facade.create("ash", "password123", "1.2.3.4");

        ArgumentCaptor<CreateUserCommand> captor = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().password()).isEqualTo("password123");
        assertThat(captor.getValue().clientIp()).isEqualTo("1.2.3.4");
    }

    @Test
    void login_sendsLoginUserCommandAndReturnsToken() throws Exception {
        LoginResult expected = new LoginResult("jwt-token", Duration.ofMinutes(15), "refresh", Duration.ofDays(30));
        when(mediator.send(any(LoginUserCommand.class))).thenReturn(expected);

        LoginResult result = facade.login("ash", "password", "1.2.3.4");

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<LoginUserCommand> captor = ArgumentCaptor.forClass(LoginUserCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().clientIp()).isEqualTo("1.2.3.4");
    }

    @Test
    void logout_sendsLogoutCommandWithRefreshToken() throws Exception {
        facade.logout("refresh");

        ArgumentCaptor<LogoutUserCommand> captor = ArgumentCaptor.forClass(LogoutUserCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().refreshToken()).isEqualTo("refresh");
    }

    @Test
    void changePassword_sendsCommand() throws Exception {
        facade.changePassword("ash", "old", "new");

        ArgumentCaptor<ChangePasswordCommand> captor = ArgumentCaptor.forClass(ChangePasswordCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ChangePasswordCommand("ash", "old", "new"));
    }

    @Test
    void searchUsers_sendsRequestingUsername() throws Exception {
        facade.searchUsers("as", "l1", "ash");

        ArgumentCaptor<SearchUsersCommand> captor = ArgumentCaptor.forClass(SearchUsersCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SearchUsersCommand("as", "l1", "ash"));
    }

    @Test
    void uploadAvatar_sendsCommandAndReturnsVersion() throws Exception {
        byte[] image = {1};
        when(mediator.send(any(UploadAvatarCommand.class))).thenReturn(42L);

        assertThat(facade.uploadAvatar("ash", image)).isEqualTo(42L);

        ArgumentCaptor<UploadAvatarCommand> captor = ArgumentCaptor.forClass(UploadAvatarCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().image()).isSameAs(image);
    }

    @Test
    void deleteAvatar_sendsCommand() throws Exception {
        facade.deleteAvatar("ash");

        verify(mediator).send(new DeleteAvatarCommand("ash"));
    }

    @Test
    void getAvatar_sendsCommandAndReturnsImage() throws Exception {
        Optional<AvatarImageResponse> image = Optional.of(new AvatarImageResponse(new byte[]{1}, "image/jpeg"));
        when(mediator.send(new GetAvatarCommand("misty", "ash"))).thenReturn(image);

        assertThat(facade.getAvatar("misty", "ash")).isSameAs(image);
    }

    @Test
    void me_sendsCommand() throws Exception {
        CurrentUserResponse me = CurrentUserResponse.builder().username("ash").build();
        when(mediator.send(new GetCurrentUserCommand("ash"))).thenReturn(me);

        assertThat(facade.me("ash")).isSameAs(me);
    }

    @Test
    void registerPushToken_sendsRegisterPushTokenCommand() throws Exception {
        facade.registerPushToken("ash", "tok-1");

        verify(mediator).send(new RegisterPushTokenCommand("ash", "tok-1"));
    }

    @Test
    void deleteAccount_sendsDeleteAccountCommandAndReturnsTheRemainingLeagues() throws Exception {
        when(mediator.send(new DeleteAccountCommand("ash", "pw"))).thenReturn(java.util.List.of("l1"));

        assertThat(facade.deleteAccount("ash", "pw")).containsExactly("l1");
    }

    @Test
    void revokeSessions_sendsRevokeSessionsCommand() throws Exception {
        facade.revokeSessions("ash");

        verify(mediator).send(new com.villu.pokefantasy.commands.users.delete.RevokeSessionsCommand("ash"));
    }

    @Test
    void unregisterPushToken_sendsUnregisterPushTokenCommand() throws Exception {
        facade.unregisterPushToken("ash", "tok-1");

        verify(mediator).send(new UnregisterPushTokenCommand("ash", "tok-1"));
    }
}
