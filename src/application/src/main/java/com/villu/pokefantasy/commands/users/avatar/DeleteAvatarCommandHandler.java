package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

/** Quita la foto de perfil: el usuario vuelve a verse con su inicial. */
@Service
public class DeleteAvatarCommandHandler implements CommandHandler<DeleteAvatarCommand, Void> {

    private final AvatarRepository avatarRepository;
    private final UserRepository userRepository;

    public DeleteAvatarCommandHandler(AvatarRepository avatarRepository, UserRepository userRepository) {
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(DeleteAvatarCommand command) {
        avatarRepository.deleteByUsername(command.username());
        userRepository.setAvatarVersion(command.username(), null);
        return null;
    }

    @Override
    public Class<DeleteAvatarCommand> commandType() {
        return DeleteAvatarCommand.class;
    }
}
