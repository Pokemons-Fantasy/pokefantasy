package com.villu.pokefantasy.commands.users.delete;

import com.villu.pokefantasy.ports.RefreshTokenPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RevokeSessionsCommandHandlerTest {

    @Test
    void handle_revokesEveryRefreshTokenOfTheUser() {
        RefreshTokenPort refreshTokenPort = mock(RefreshTokenPort.class);
        RevokeSessionsCommandHandler handler = new RevokeSessionsCommandHandler(refreshTokenPort);

        handler.handle(new RevokeSessionsCommand("ash"));

        verify(refreshTokenPort).revokeAll("ash");
        assertThat(handler.commandType()).isEqualTo(RevokeSessionsCommand.class);
    }
}
