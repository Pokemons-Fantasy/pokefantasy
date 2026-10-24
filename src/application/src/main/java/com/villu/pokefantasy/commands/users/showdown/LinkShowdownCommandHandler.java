package com.villu.pokefantasy.commands.users.showdown;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Vincula una cuenta de Showdown al usuario. No se verifica que sea suya: un resultado solo se acepta si
 * los jugadores del replay son los vinculados, así que suplantar a otro no da nada. Una cuenta de
 * Showdown solo puede estar vinculada a un jugador (también lo garantiza el índice único).
 */
@Service
public class LinkShowdownCommandHandler implements CommandHandler<LinkShowdownCommand, Void> {

    private final UserRepository userRepository;

    public LinkShowdownCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(LinkShowdownCommand command) {
        userRepository.findByShowdownId(command.showdownId())
                .filter(owner -> !owner.getName().equals(command.username()))
                .ifPresent(owner -> {
                    throw new IllegalStateException(
                            "La cuenta de Showdown '" + command.showdownName() + "' ya está vinculada a otro jugador.");
                });
        userRepository.setShowdownAccount(command.username(), command.showdownName(), command.showdownId());
        return null;
    }

    @Override
    public Class<LinkShowdownCommand> commandType() {
        return LinkShowdownCommand.class;
    }
}
