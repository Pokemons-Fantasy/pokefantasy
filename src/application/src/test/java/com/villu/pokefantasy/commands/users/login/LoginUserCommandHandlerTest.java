package com.villu.pokefantasy.commands.users.login;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.ports.LoginAttemptPort;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserCommandHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordHashPort passwordHashPort;
    @Mock private TokenPort tokenPort;
    @Mock private RefreshTokenPort refreshTokenPort;

    private static final String IP = "1.2.3.4";

    private InMemoryLoginAttempts attempts;
    private LoginUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        attempts = new InMemoryLoginAttempts();
        handler = new LoginUserCommandHandler(userRepository, userMapper, passwordHashPort, tokenPort, attempts,
                refreshTokenPort);
    }

    @Test
    void handle_nullCommand_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("", "pass", IP)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyPassword_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ash", "", IP)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_userNotFound_throwsBadCredentials() {
        when(userRepository.findByUsername("unknown")).thenReturn(null);
        when(userMapper.entityToDto(null)).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("unknown", "pass", IP)))
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

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ash", "wrong", IP)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void handle_validCredentials_returnsAccessAndRefreshTokens() {
        UserEntity entity = new UserEntity();
        entity.setName("ash");
        entity.setPassword("hashed");
        User user = User.builder().name("ash").password("hashed").build();

        when(userRepository.findByUsername("ash")).thenReturn(entity);
        when(userMapper.entityToDto(entity)).thenReturn(user);
        when(passwordHashPort.matches("secret", "hashed")).thenReturn(true);
        when(tokenPort.generateToken("ash")).thenReturn("jwt.token.here");
        when(tokenPort.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(refreshTokenPort.issue("ash")).thenReturn("refresh-token");
        when(refreshTokenPort.ttl()).thenReturn(Duration.ofDays(30));

        LoginResult result = handler.handle(new LoginUserCommand("ash", "secret", IP));

        assertThat(result).isEqualTo(new LoginResult("jwt.token.here", Duration.ofMinutes(15),
                "refresh-token", Duration.ofDays(30)));
    }

    // ── Límite de intentos ────────────────────────────────────────────────────

    @Test
    void handle_failedLogin_recordsFailureForUserAndIp() {
        stubUser("ash", "hashed");
        when(passwordHashPort.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ash", "wrong", IP)))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(attempts.failureCount("user:ash")).isEqualTo(1);
        assertThat(attempts.failureCount("ip:" + IP)).isEqualTo(1);
        assertThat(attempts.windows).containsValue(LoginUserCommandHandler.LOCKOUT_WINDOW);
    }

    @Test
    void handle_unknownUser_alsoCountsAsFailure() {
        when(userRepository.findByUsername("ghost")).thenReturn(null);
        when(userMapper.entityToDto(null)).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ghost", "pass", IP)))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(attempts.failureCount("user:ghost")).isEqualTo(1);
    }

    @Test
    void handle_userAtFailureLimit_blockedEvenWithCorrectPassword() {
        attempts.counts.put("user:ash", (long) LoginUserCommandHandler.MAX_FAILURES_PER_USER);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ash", "secret", IP)))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessageContaining("15 minutos");

        // Ni siquiera se consulta la contraseña: no hay oráculo mientras está bloqueado.
        verifyNoInteractions(userRepository, passwordHashPort, tokenPort);
    }

    @Test
    void handle_userKey_isCaseInsensitive() {
        attempts.counts.put("user:ash", (long) LoginUserCommandHandler.MAX_FAILURES_PER_USER);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ASH", "secret", IP)))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void handle_ipAtFailureLimit_blocksAnyUserFromThatIp() {
        attempts.counts.put("ip:" + IP, (long) LoginUserCommandHandler.MAX_FAILURES_PER_IP);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("misty", "secret", IP)))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void handle_belowLimits_loginStillAllowed() {
        attempts.counts.put("user:ash", (long) LoginUserCommandHandler.MAX_FAILURES_PER_USER - 1);
        attempts.counts.put("ip:" + IP, (long) LoginUserCommandHandler.MAX_FAILURES_PER_IP - 1);
        stubUser("ash", "hashed");
        when(passwordHashPort.matches("secret", "hashed")).thenReturn(true);
        when(tokenPort.generateToken("ash")).thenReturn("jwt");

        assertThat(handler.handle(new LoginUserCommand("ash", "secret", IP)).accessToken()).isEqualTo("jwt");
    }

    @Test
    void handle_successfulLogin_clearsUserFailuresButNotIp() {
        attempts.counts.put("user:ash", 3L);
        attempts.counts.put("ip:" + IP, 3L);
        stubUser("ash", "hashed");
        when(passwordHashPort.matches("secret", "hashed")).thenReturn(true);
        when(tokenPort.generateToken("ash")).thenReturn("jwt");

        handler.handle(new LoginUserCommand("ash", "secret", IP));

        assertThat(attempts.failureCount("user:ash")).isZero();
        assertThat(attempts.failureCount("ip:" + IP)).isEqualTo(3);
    }

    @Test
    void handle_withoutClientIp_onlyLimitsByUser() {
        stubUser("ash", "hashed");
        when(passwordHashPort.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginUserCommand("ash", "wrong", null)))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(attempts.counts).containsOnlyKeys("user:ash");
    }

    @Test
    void handle_blankClientIp_onlyLimitsByUser() {
        attempts.counts.put("ip:", 1000L);
        stubUser("ash", "hashed");
        when(passwordHashPort.matches("secret", "hashed")).thenReturn(true);
        when(tokenPort.generateToken("ash")).thenReturn("jwt");

        assertThat(handler.handle(new LoginUserCommand("ash", "secret", " ")).accessToken()).isEqualTo("jwt");
    }

    private void stubUser(String name, String hashedPassword) {
        UserEntity entity = new UserEntity();
        entity.setName(name);
        entity.setPassword(hashedPassword);
        when(userRepository.findByUsername(name)).thenReturn(entity);
        when(userMapper.entityToDto(entity)).thenReturn(User.builder().name(name).password(hashedPassword).build());
    }

    /** Contador en memoria: permite probar la política real sin Redis. */
    private static final class InMemoryLoginAttempts implements LoginAttemptPort {
        final Map<String, Long> counts = new HashMap<>();
        final Map<String, Duration> windows = new HashMap<>();

        @Override
        public long failureCount(String key) {
            return counts.getOrDefault(key, 0L);
        }

        @Override
        public void recordFailure(String key, Duration window) {
            counts.merge(key, 1L, Long::sum);
            windows.put(key, window);
        }

        @Override
        public void clearFailures(String key) {
            counts.remove(key);
        }
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(LoginUserCommand.class);
    }
}
