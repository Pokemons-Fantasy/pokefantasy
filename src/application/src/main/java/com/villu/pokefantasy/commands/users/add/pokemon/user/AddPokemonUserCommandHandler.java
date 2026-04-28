package com.villu.pokefantasy.commands.users.add.pokemon.user;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

@Service
public class AddPokemonUserCommandHandler implements CommandHandler<AddPokemonUserCommand, Void> {

    private UserRepository userRepository;

    @Override
    public Void handle(AddPokemonUserCommand command) throws Exception {

        UserEntity user = userRepository.findByUsername(command.nameUser());
        if (user == null) {
            throw new Exception("User not found");
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
