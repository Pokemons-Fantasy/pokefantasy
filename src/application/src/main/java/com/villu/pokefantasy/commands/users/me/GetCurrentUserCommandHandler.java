package com.villu.pokefantasy.commands.users.me;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import com.villu.pokefantasy.response.CurrentUserResponse;
import org.springframework.stereotype.Service;

/** Datos del usuario en sesión que el front no guarda (por ahora, la versión de su foto). */
@Service
public class GetCurrentUserCommandHandler implements CommandHandler<GetCurrentUserCommand, CurrentUserResponse> {

    private final UserRepository userRepository;

    public GetCurrentUserCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public CurrentUserResponse handle(GetCurrentUserCommand command) {
        UserEntity user = userRepository.findByUsername(command.username());
        if (user == null) {
            throw new IllegalArgumentException("No existe el usuario '" + command.username() + "'");
        }
        return CurrentUserResponse.builder()
                .username(user.getName())
                .avatarVersion(user.getAvatarVersion())
                .build();
    }

    @Override
    public Class<GetCurrentUserCommand> commandType() {
        return GetCurrentUserCommand.class;
    }
}
