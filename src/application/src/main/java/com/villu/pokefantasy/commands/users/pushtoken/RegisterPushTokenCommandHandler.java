package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class RegisterPushTokenCommandHandler
        implements CommandHandler<RegisterPushTokenCommand, Void> {

    private final UserRepository userRepository;

    public RegisterPushTokenCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(RegisterPushTokenCommand command) {
        if (command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("Falta el token de notificaciones.");
        }
        // Un token es de un dispositivo y de quien lo registra: si otra cuenta lo tenía (mismo navegador,
        // sesión caducada sin cerrar), deja de recibir los avisos de esta.
        userRepository.removeFcmToken(command.token());
        userRepository.addFcmToken(command.username(), command.token());
        return null;
    }

    @Override
    public Class<RegisterPushTokenCommand> commandType() {
        return RegisterPushTokenCommand.class;
    }
}
