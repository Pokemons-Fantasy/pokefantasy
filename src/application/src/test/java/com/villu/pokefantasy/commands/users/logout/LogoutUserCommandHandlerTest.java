package com.villu.pokefantasy.commands.users.logout;

import com.villu.pokefantasy.ports.RefreshTokenPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LogoutUserCommandHandlerTest {

    @Mock private RefreshTokenPort refreshTokenPort;

    private LogoutUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LogoutUserCommandHandler(refreshTokenPort);
    }

    @Test
    void handle_withRefreshToken_revokesIt() {
        handler.handle(new LogoutUserCommand("refresh-token"));

        verify(refreshTokenPort).revoke("refresh-token");
    }

    @Test
    void handle_withoutRefreshToken_nothingToRevoke() {
        handler.handle(new LogoutUserCommand(null));
        handler.handle(new LogoutUserCommand(" "));

        verifyNoInteractions(refreshTokenPort);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(LogoutUserCommand.class);
    }
}
