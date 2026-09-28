package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.response.AvatarImageResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Foto de perfil de cualquier usuario (la ven los demás jugadores); vacío si no tiene. */
@Service
public class GetAvatarCommandHandler implements CommandHandler<GetAvatarCommand, Optional<AvatarImageResponse>> {

    private final AvatarRepository avatarRepository;

    public GetAvatarCommandHandler(AvatarRepository avatarRepository) {
        this.avatarRepository = avatarRepository;
    }

    @Override
    public Optional<AvatarImageResponse> handle(GetAvatarCommand command) {
        return avatarRepository.findByUsername(command.username())
                .map(a -> new AvatarImageResponse(a.getData(), a.getContentType()));
    }

    @Override
    public Class<GetAvatarCommand> commandType() {
        return GetAvatarCommand.class;
    }
}
