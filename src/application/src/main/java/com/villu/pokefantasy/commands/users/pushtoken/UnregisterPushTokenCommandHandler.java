package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

/** Da de baja un dispositivo (un navegador que desactiva los avisos o cierra sesión). */
@Service
public class UnregisterPushTokenCommandHandler implements CommandHandler<UnregisterPushTokenCommand, Void> {

    private final UserRepository userRepository;

    public UnregisterPushTokenCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(UnregisterPushTokenCommand command) {
        if (command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("Falta el token de notificaciones.");
        }
        userRepository.removeFcmToken(command.username(), command.token());
        return null;
    }

    @Override
    public Class<UnregisterPushTokenCommand> commandType() {
        return UnregisterPushTokenCommand.class;
    }
}
