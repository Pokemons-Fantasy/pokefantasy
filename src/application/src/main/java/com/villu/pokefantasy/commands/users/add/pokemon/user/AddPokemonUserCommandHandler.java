package com.villu.pokefantasy.commands.users.add.pokemon.user;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class AddPokemonUserCommandHandler implements CommandHandler<AddPokemonUserCommand, Void> {

    private static final int MAX_POKEMONS_PER_USER = 10;

    private final UserRepository userRepository;

    public AddPokemonUserCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(AddPokemonUserCommand command) {
        if (command == null || command.nameUser() == null || command.nameUser().isBlank() || command.pokemons() == null) {
            throw new IllegalArgumentException("Username and pokemons are required");
        }

        String username = command.nameUser().trim();
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        if (user.getPokemons() == null) {
            user.setPokemons(new ArrayList<>());
        }

        int available = MAX_POKEMONS_PER_USER - user.getPokemons().size();
        if (available <= 0) {
            throw new IllegalStateException("User already has the maximum of " + MAX_POKEMONS_PER_USER + " Pokémon");
        }
        if (command.pokemons().size() > available) {
            throw new IllegalStateException("Adding " + command.pokemons().size()
                    + " Pokémon would exceed the limit of " + MAX_POKEMONS_PER_USER
                    + ". Slots available: " + available);
        }

        user.getPokemons().addAll(command.pokemons());
        userRepository.updateUserWithPokemons(user);
        return null;
    }

    @Override
    public Class<AddPokemonUserCommand> commandType() {
        return AddPokemonUserCommand.class;
    }
}
