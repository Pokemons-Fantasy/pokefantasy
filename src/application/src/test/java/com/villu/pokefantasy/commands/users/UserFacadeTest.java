package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.add.pokemon.user.AddPokemonUserCommand;
import com.villu.pokefantasy.commands.users.create.CreateUserCommand;
import com.villu.pokefantasy.commands.users.login.LoginUserCommand;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
        when(mediator.send(any(LoginUserCommand.class))).thenReturn("jwt-token");

        String token = facade.login("ash", "password");

        assertThat(token).isEqualTo("jwt-token");
        ArgumentCaptor<LoginUserCommand> captor = ArgumentCaptor.forClass(LoginUserCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("ash");
    }

    @Test
    void addPokemonsToUser_sendsAddPokemonUserCommand() throws Exception {
        Pokemons poke = new Pokemons(25, "pikachu", null, null, null, null, null, null);

        facade.addPokemonsToUser("ash", List.of(poke));

        ArgumentCaptor<AddPokemonUserCommand> captor = ArgumentCaptor.forClass(AddPokemonUserCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().nameUser()).isEqualTo("ash");
        assertThat(captor.getValue().pokemons()).hasSize(1);
    }
}
