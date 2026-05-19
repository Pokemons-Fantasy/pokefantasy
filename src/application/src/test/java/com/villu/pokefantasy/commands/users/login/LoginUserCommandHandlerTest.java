package com.villu.pokefantasy.commands.users.login;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserCommandHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordHashPort passwordHashPort;
    @Mock private TokenPort tokenPort;

    private LoginUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LoginUserCommandHandler(userRepository, userMapper, passwordHashPort, tokenPort);
    }

    @Test
    void handle_nullCommand_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("", "pass")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyPassword_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ash", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_userNotFound_throwsBadCredentials() {
        when(userRepository.findByUsername("unknown")).thenReturn(null);
        when(userMapper.entityToDto(null)).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("unknown", "pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void handle_wrongPassword_throwsBadCredentials() {
        UserEntity entity = new UserEntity();
        entity.setName("ash");
        entity.setPassword("hashed");
        User user = User.builder().name("ash").password("hashed").build();

        when(userRepository.findByUsername("ash")).thenReturn(entity);
        when(userMapper.entityToDto(entity)).thenReturn(user);
        when(passwordHashPort.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ash", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void handle_validCredentials_returnsToken() {
        UserEntity entity = new UserEntity();
        entity.setName("ash");
        entity.setPassword("hashed");
        User user = User.builder().name("ash").password("hashed").build();

        when(userRepository.findByUsername("ash")).thenReturn(entity);
        when(userMapper.entityToDto(entity)).thenReturn(user);
        when(passwordHashPort.matches("secret", "hashed")).thenReturn(true);
        when(tokenPort.generateToken("ash")).thenReturn("jwt.token.here");

        String result = handler.handle(new LoginUserCommand("ash", "secret"));

        assertThat(result).isEqualTo("jwt.token.here");
        verify(tokenPort).generateToken("ash");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(LoginUserCommand.class);
    }
}
