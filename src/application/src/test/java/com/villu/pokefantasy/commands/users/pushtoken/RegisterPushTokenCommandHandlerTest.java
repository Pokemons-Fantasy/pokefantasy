package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class RegisterPushTokenCommandHandlerTest {

    @Mock private UserRepository userRepository;
    private RegisterPushTokenCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RegisterPushTokenCommandHandler(userRepository);
    }

    @Test
    void handle_validToken_takesItFromAnyOtherUserFirst() throws Exception {
        handler.handle(new RegisterPushTokenCommand("ash", "fcm-token-123"));

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).removeFcmToken("fcm-token-123");
        order.verify(userRepository).addFcmToken("ash", "fcm-token-123");
    }

    @Test
    void handle_nullToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new RegisterPushTokenCommand("ash", null)));
    }

    @Test
    void handle_blankToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(new RegisterPushTokenCommand("ash", "  ")));
    }
}
