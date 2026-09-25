package com.villu.pokefantasy.commands.users.logout;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import org.springframework.stereotype.Service;

/** Revoca el refresh token: el JWT de acceso que ya se emitió caduca solo en unos minutos. */
@Service
public class LogoutUserCommandHandler implements CommandHandler<LogoutUserCommand, Void> {

    private final RefreshTokenPort refreshTokenPort;

    public LogoutUserCommandHandler(RefreshTokenPort refreshTokenPort) {
        this.refreshTokenPort = refreshTokenPort;
    }

    @Override
    public Void handle(LogoutUserCommand command) {
        if (command.refreshToken() != null && !command.refreshToken().isBlank()) {
            refreshTokenPort.revoke(command.refreshToken());
        }
        return null;
    }

    @Override
    public Class<LogoutUserCommand> commandType() {
        return LogoutUserCommand.class;
    }
}
