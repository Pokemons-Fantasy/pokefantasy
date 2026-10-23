package com.villu.pokefantasy.commands.users.delete;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import org.springframework.stereotype.Service;

/**
 * Revoca todas las sesiones de un usuario. Tras borrar la cuenta se llama otra vez, ya fuera de su transacción:
 * un login hecho mientras se borraba habría dejado un refresh vivo, y cuando otro registrase el nombre ese refresh
 * entraría en la cuenta nueva.
 */
@Service
public class RevokeSessionsCommandHandler implements CommandHandler<RevokeSessionsCommand, Void> {

    private final RefreshTokenPort refreshTokenPort;

    public RevokeSessionsCommandHandler(RefreshTokenPort refreshTokenPort) {
        this.refreshTokenPort = refreshTokenPort;
    }

    @Override
    public Void handle(RevokeSessionsCommand command) {
        refreshTokenPort.revokeAll(command.username());
        return null;
    }

    @Override
    public Class<RevokeSessionsCommand> commandType() {
        return RevokeSessionsCommand.class;
    }
}
