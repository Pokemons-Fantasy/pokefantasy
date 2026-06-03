package com.villu.pokefantasy.commands.users.pushtoken;

import com.villu.pokefantasy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegisterPushTokenCommandHandlerTest {

    @Mock private UserRepository userRepository;
    private RegisterPushTokenCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RegisterPushTokenCommandHandler(userRepository);
    }

    @Test
    void handle_validToken_callsAddFcmToken() throws Exception {
        handler.handle(new RegisterPushTokenCommand("ash", "fcm-token-123"));
        verify(userRepository).addFcmToken("ash", "fcm-token-123");
    }
}
