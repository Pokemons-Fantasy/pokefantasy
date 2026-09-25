package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.create.CreateUserCommand;
import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.commands.users.login.LoginUserCommand;
import com.villu.pokefantasy.commands.users.logout.LogoutUserCommand;
import com.villu.pokefantasy.commands.users.search.SearchUsersCommand;
import com.villu.pokefantasy.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFacadeTest {

    @Mock private Mediator mediator;

    private UserFacade facade;

    @BeforeEach
    void setUp() {
        facade = new UserFacade(mediator);
    }

    @Test
    void create_sendsCreateUserCommand() throws Exception {
        facade.create("ash", "password123");

        ArgumentCaptor<CreateUserCommand> captor = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().password()).isEqualTo("password123");
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
    void searchUsers_sendsRequestingUsername() throws Exception {
        facade.searchUsers("as", "l1", "ash");

        ArgumentCaptor<SearchUsersCommand> captor = ArgumentCaptor.forClass(SearchUsersCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SearchUsersCommand("as", "l1", "ash"));
    }
}
