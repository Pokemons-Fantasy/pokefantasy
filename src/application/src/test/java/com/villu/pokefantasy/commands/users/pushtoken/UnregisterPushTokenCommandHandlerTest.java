package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UnregisterPushTokenCommandHandlerTest {

    @Mock private UserRepository userRepository;
    private UnregisterPushTokenCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UnregisterPushTokenCommandHandler(userRepository);
    }

    @Test
    void handle_removesTheTokenOnlyFromTheRequester() {
        handler.handle(new UnregisterPushTokenCommand("ash", "tok-1"));
        verify(userRepository).removeFcmToken("ash", "tok-1");
    }

    @Test
    void handle_blankToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(new UnregisterPushTokenCommand("ash", " ")));
        verifyNoInteractions(userRepository);
    }

    @Test
    void handle_nullToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> handler.handle(new UnregisterPushTokenCommand("ash", null)));
        verifyNoInteractions(userRepository);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(UnregisterPushTokenCommand.class);
    }
}
