package com.villu.pokefantasy.commands.users.avatar;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.AvatarEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Guarda la foto de perfil (sobrescribe la anterior) y devuelve su versión, que es el instante de la
 * subida en ms. Repetirlo tras un reintento de la transacción solo cambia la versión.
 */
@Service
public class UploadAvatarCommandHandler implements CommandHandler<UploadAvatarCommand, Long> {

    private final AvatarRepository avatarRepository;
    private final UserRepository userRepository;

    public UploadAvatarCommandHandler(AvatarRepository avatarRepository, UserRepository userRepository) {
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Long handle(UploadAvatarCommand command) {
        AvatarImagePolicy.validate(command.image());
        Instant now = Instant.now();
        avatarRepository.save(new AvatarEntity(
                command.username(), command.image(), AvatarImagePolicy.CONTENT_TYPE, now));
        long version = now.toEpochMilli();
        userRepository.setAvatarVersion(command.username(), version);
        return version;
    }

    @Override
    public Class<UploadAvatarCommand> commandType() {
        return UploadAvatarCommand.class;
    }
}
