package com.villu.pokefantasy.commands.users.password;

import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.ports.LoginAttemptPort;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordCommandHandlerTest {

    private static final String OLD = "old-password";
    private static final String NEW = "new-password";

    @Mock private UserRepository userRepository;
    @Mock private PasswordHashPort passwordHashPort;
    @Mock private LoginAttemptPort loginAttemptPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private TokenPort tokenPort;

    private ChangePasswordCommandHandler handler;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        handler = new ChangePasswordCommandHandler(userRepository, passwordHashPort, loginAttemptPort,
                refreshTokenPort, tokenPort);
        user = new UserEntity();
        user.setName("Ash");
        user.setPassword("hash-old");
        lenient().when(userRepository.findByUsername("Ash")).thenReturn(user);
        lenient().when(passwordHashPort.matches(OLD, "hash-old")).thenReturn(true);
        lenient().when(passwordHashPort.encode(NEW)).thenReturn("hash-new");
        lenient().when(tokenPort.generateToken("Ash")).thenReturn("jwt");
        lenient().when(tokenPort.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        lenient().when(refreshTokenPort.issue("Ash")).thenReturn("refresh");
        lenient().when(refreshTokenPort.ttl()).thenReturn(Duration.ofDays(30));
    }

    @Test
    void changesPassword_revokesEverySession_andIssuesANewOne() {
        LoginResult result = handler.handle(new ChangePasswordCommand("Ash", OLD, NEW));

        assertThat(user.getPassword()).isEqualTo("hash-new");
        verify(userRepository).saveUser(user);
        verify(loginAttemptPort).clearFailures("user:ash");
        // Primero se revocan todas y después se emite la nueva: si no, la nueva también caería.
        InOrder order = inOrder(refreshTokenPort);
        order.verify(refreshTokenPort).revokeAll("Ash");
        order.verify(refreshTokenPort).issue("Ash");
        assertThat(result).isEqualTo(new LoginResult("jwt", Duration.ofMinutes(15), "refresh", Duration.ofDays(30)));
    }

    @Test
    void wrongCurrentPassword_countsAsFailedLogin() {
        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", "nope", NEW)))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptPort).recordFailure("user:ash", Duration.ofMinutes(15));
        verify(userRepository, never()).saveUser(any());
        verify(refreshTokenPort, never()).revokeAll(anyString());
    }

    @Test
    void unknownUser_badCredentials() {
        when(userRepository.findByUsername("Ash")).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", OLD, NEW)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void lockedOut_rejectedEvenWithTheRightPassword() {
        when(loginAttemptPort.failureCount("user:ash")).thenReturn(5L);

        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", OLD, NEW)))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(userRepository, never()).saveUser(any());
    }

    @Test
    void newPasswordTooShort_badRequest_beforeCheckingAnything() {
        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", OLD, "short")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 8");
        verify(loginAttemptPort, never()).recordFailure(anyString(), any());
    }

    @Test
    void newPasswordTooLong_badRequest() {
        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", OLD, "ñ".repeat(37))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado larga");
    }

    @Test
    void missingCurrentPassword_badRequest() {
        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", "", NEW)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", null, NEW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameAsCurrent_badRequest() {
        when(passwordHashPort.matches(NEW, "hash-old")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new ChangePasswordCommand("Ash", OLD, NEW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinta");
        verify(userRepository, never()).saveUser(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(ChangePasswordCommand.class);
    }
}
