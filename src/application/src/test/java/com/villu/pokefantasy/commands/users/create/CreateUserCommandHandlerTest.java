package com.villu.pokefantasy.commands.users.create;

import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.ports.LoginAttemptPort;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserCommandHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordHashPort passwordHashPort;
    @Mock private LoginAttemptPort loginAttemptPort;

    private CreateUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateUserCommandHandler(userRepository, userMapper, passwordHashPort, loginAttemptPort);
    }

    @Test
    void handle_nullCommand_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_nullUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand(null, "pass", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("", "pass", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_nullPassword_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("user", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyPassword_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("user", "", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_validCommand_encodesPasswordAndSavesUser() {
        when(passwordHashPort.encode("secret123")).thenReturn("hashed");
        UserEntity entity = new UserEntity();
        when(userMapper.dtoToEntity(any())).thenReturn(entity);

        handler.handle(new CreateUserCommand("ash", "secret123", null));

        verify(passwordHashPort).encode("secret123");
        verify(userMapper).dtoToEntity(argThat(u -> "ash".equals(u.getName()) && "hashed".equals(u.getPassword())));
        verify(userRepository).saveUser(entity);
    }

    @Test
    void handle_validCommand_generatesUniqueId() {
        when(passwordHashPort.encode(any())).thenReturn("hashed");
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand("ash", "secret123", null));

        ArgumentCaptor<com.villu.pokefantasy.dto.users.User> captor =
                ArgumentCaptor.forClass(com.villu.pokefantasy.dto.users.User.class);
        verify(userMapper).dtoToEntity(captor.capture());
        assertThat(captor.getValue().getId()).isNotBlank();
    }

    // ── Validación del registro ───────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ab", "abcdefghijklmnopqrstu", "ash ketchum", "ash!", "ásh", "<script>"})
    void handle_invalidUsername_throwsIllegalArgumentWithoutSaving(String username) {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand(username, "secret123", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre de usuario");
        verifyNoInteractions(userRepository, passwordHashPort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ash", "Ash_Ketchum", "gary-oak", "abcdefghijklmnopqrst"})
    void handle_validUsernames_areAccepted(String username) {
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand(username, "secret123", null));

        verify(userRepository).saveUser(any());
    }

    @Test
    void handle_passwordTooShort_throwsIllegalArgumentWithoutSaving() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("ash", "1234567", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 8");
        verifyNoInteractions(userRepository, passwordHashPort);
    }

    @Test
    void handle_passwordLongerThanBcryptLimit_throwsIllegalArgument() {
        // 37 caracteres de 2 bytes = 74 bytes > 72 que bcrypt realmente usa
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("ash", "ñ".repeat(37), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado larga");
        verifyNoInteractions(userRepository, passwordHashPort);
    }

    @Test
    void handle_passwordExactly72Bytes_isAccepted() {
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand("ash", "a".repeat(72), null));

        verify(userRepository).saveUser(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(CreateUserCommand.class);
    }

    @Test
    void handle_nameAlreadyTakenIgnoringCase_isAClearConflict() {
        when(userRepository.existsByUsernameIgnoreCase("lobon")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("lobon", "pikachu123", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ese nombre de usuario ya está en uso.");
        verify(userRepository, never()).saveUser(any());
    }

    // ── Límite de registros por IP ────────────────────────────────────────────

    @Test
    void handle_ipAtLimit_throwsTooManyAttempts_andDoesNotSave() {
        when(loginAttemptPort.failureCount("register-ip:1.2.3.4")).thenReturn(3L);

        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("ash", "secret123", "1.2.3.4")))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofHours(1)));
        verify(userRepository, never()).saveUser(any());
        verifyNoInteractions(passwordHashPort);
    }

    @Test
    void handle_ipv6_limitCountsByItsSlash64() {
        when(loginAttemptPort.failureCount("register-ip:2001:db8:1:2::/64")).thenReturn(3L);

        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("ash", "secret123", "2001:db8:1:2::77")))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(userRepository, never()).saveUser(any());
    }

    @Test
    void handle_ipBelowLimit_savesAndRecordsRegistration() {
        when(loginAttemptPort.failureCount("register-ip:1.2.3.4")).thenReturn(2L);
        when(loginAttemptPort.recordFailure("register-ip:1.2.3.4", Duration.ofHours(1))).thenReturn(3L);
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand("ash", "secret123", "1.2.3.4"));

        verify(userRepository).saveUser(any());
    }

    @Test
    void handle_concurrentRegistrationsOverLimit_areRejectedWithoutSaving() {
        // Dos peticiones a la vez leen 2: el INCR atómico decide quién se queda el último hueco.
        when(loginAttemptPort.failureCount("register-ip:1.2.3.4")).thenReturn(2L);
        when(loginAttemptPort.recordFailure("register-ip:1.2.3.4", Duration.ofHours(1))).thenReturn(4L);

        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("ash", "secret123", "1.2.3.4")))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(userRepository, never()).saveUser(any());
        verifyNoInteractions(passwordHashPort);
    }

    @Test
    void handle_failedRegistration_doesNotConsumeQuota() {
        when(userRepository.existsByUsernameIgnoreCase("lobon")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new CreateUserCommand("lobon", "pikachu123", "1.2.3.4")))
                .isInstanceOf(IllegalStateException.class);
        verify(loginAttemptPort, never()).recordFailure(any(), any());
    }

    @Test
    void handle_withoutIp_doesNotTouchRateLimit() {
        when(userMapper.dtoToEntity(any())).thenReturn(new UserEntity());

        handler.handle(new CreateUserCommand("ash", "secret123", " "));

        verify(userRepository).saveUser(any());
        verifyNoInteractions(loginAttemptPort);
    }
}
