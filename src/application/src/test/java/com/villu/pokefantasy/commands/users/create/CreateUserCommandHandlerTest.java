package com.villu.pokefantasy.commands.users.create;

import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserCommandHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordHashPort passwordHashPort;

    private CreateUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateUserCommandHandler(userRepository, userMapper, passwordHashPort);
    }

    @Test
    void handle_nullCommand_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_nullUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand(null, "pass")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("", "pass")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_nullPassword_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("user", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyPassword_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("user", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_validCommand_encodesPasswordAndSavesUser() {
        when(passwordHashPort.encode("secret123")).thenReturn("hashed");
        UserEntity entity = new UserEntity();
        when(userMapper.dtoToEntity(any())).thenReturn(entity);

        handler.handle(new CreateUserCommand("ash", "secret123"));

        verify(passwordHashPort).encode("secret123");
        verify(userMapper).dtoToEntity(argThat(u -> "ash".equals(u.getName()) && "hashed".equals(u.getPassword())));
        verify(userRepository).saveUser(entity);
    }

    @Test
    void handle_validCommand_generatesUniqueId() {
        when(passwordHashPort.encode(any())).thenReturn("hashed");
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand("ash", "secret123"));

        ArgumentCaptor<com.villu.pokefantasy.dto.users.User> captor =
                ArgumentCaptor.forClass(com.villu.pokefantasy.dto.users.User.class);
        verify(userMapper).dtoToEntity(captor.capture());
        assertThat(captor.getValue().getId()).isNotBlank();
    }

    // ── Validación del registro ───────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ab", "abcdefghijklmnopqrstu", "ash ketchum", "ash!", "ásh", "<script>"})
    void handle_invalidUsername_throwsIllegalArgumentWithoutSaving(String username) {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand(username, "secret123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre de usuario");
        verifyNoInteractions(userRepository, passwordHashPort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ash", "Ash_Ketchum", "gary-oak", "abcdefghijklmnopqrst"})
    void handle_validUsernames_areAccepted(String username) {
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand(username, "secret123"));

        verify(userRepository).saveUser(any());
    }

    @Test
    void handle_passwordTooShort_throwsIllegalArgumentWithoutSaving() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("ash", "1234567")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 8");
        verifyNoInteractions(userRepository, passwordHashPort);
    }

    @Test
    void handle_passwordLongerThanBcryptLimit_throwsIllegalArgument() {
        // 37 caracteres de 2 bytes = 74 bytes > 72 que bcrypt realmente usa
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("ash", "ñ".repeat(37))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado larga");
        verifyNoInteractions(userRepository, passwordHashPort);
    }

    @Test
    void handle_passwordExactly72Bytes_isAccepted() {
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand("ash", "a".repeat(72)));

        verify(userRepository).saveUser(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(CreateUserCommand.class);
    }
}
