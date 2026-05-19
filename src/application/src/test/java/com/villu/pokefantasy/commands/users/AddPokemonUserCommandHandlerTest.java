package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.add.pokemon.user.AddPokemonUserCommand;
import com.villu.pokefantasy.commands.users.add.pokemon.user.AddPokemonUserCommandHandler;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddPokemonUserCommandHandlerTest {

    @Mock private UserRepository userRepository;

    private AddPokemonUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddPokemonUserCommandHandler(userRepository);
    }

    @Test
    void handle_nullUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AddPokemonUserCommand(List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AddPokemonUserCommand(List.of(), "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_nullPokemons_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AddPokemonUserCommand(null, "ash")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_userNotFound_throwsIllegalArgument() {
        when(userRepository.findByUsername("ash")).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new AddPokemonUserCommand(List.of(), "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void handle_userAtMaxPokemons_throwsIllegalState() {
        UserEntity user = new UserEntity();
        List<Pokemons> existing = new ArrayList<>();
        for (int i = 0; i < 10; i++) existing.add(new Pokemons());
        user.setPokemons(existing);
        when(userRepository.findByUsername("ash")).thenReturn(user);

        assertThatThrownBy(() -> handler.handle(new AddPokemonUserCommand(List.of(new Pokemons()), "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void handle_addingTooManyPokemons_throwsIllegalState() {
        UserEntity user = new UserEntity();
        List<Pokemons> existing = new ArrayList<>();
        for (int i = 0; i < 9; i++) existing.add(new Pokemons());
        user.setPokemons(existing);
        when(userRepository.findByUsername("ash")).thenReturn(user);

        List<Pokemons> toAdd = List.of(new Pokemons(), new Pokemons());
        assertThatThrownBy(() -> handler.handle(new AddPokemonUserCommand(toAdd, "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceed");
    }

    @Test
    void handle_userWithNullPokemonsList_initializesListAndAdds() {
        UserEntity user = new UserEntity();
        user.setPokemons(null);
        when(userRepository.findByUsername("ash")).thenReturn(user);

        Pokemons poke = new Pokemons(25, "pikachu", null, null, null, null, null, null);
        handler.handle(new AddPokemonUserCommand(List.of(poke), "ash"));

        verify(userRepository).updateUserWithPokemons(user);
        assertThat(user.getPokemons()).hasSize(1);
    }

    @Test
    void handle_validCommand_addsPokemons() {
        UserEntity user = new UserEntity();
        user.setPokemons(new ArrayList<>());
        when(userRepository.findByUsername("ash")).thenReturn(user);

        Pokemons poke = new Pokemons(25, "pikachu", null, null, null, null, null, null);
        handler.handle(new AddPokemonUserCommand(List.of(poke), "ash"));

        verify(userRepository).updateUserWithPokemons(user);
        assertThat(user.getPokemons()).hasSize(1);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(AddPokemonUserCommand.class);
    }
}
