package com.villu.pokefantasy.commands.users.login;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.LoginAttemptPort;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class LoginUserCommandHandler implements CommandHandler<LoginUserCommand, LoginResult> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordHashPort passwordHashPort;
    private final TokenPort tokenPort;
    private final LoginAttemptPort loginAttemptPort;
    private final RefreshTokenPort refreshTokenPort;

    /** Fallos por usuario antes de bloquearlo: frena la fuerza bruta contra una cuenta. */
    public static final int MAX_FAILURES_PER_USER = 5;
    /** Fallos por IP antes de bloquearla: frena probar una contraseña contra muchas cuentas. */
    static final int MAX_FAILURES_PER_IP = 30;
    public static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    public LoginUserCommandHandler(UserRepository userRepository, UserMapper userMapper,
                                   PasswordHashPort passwordHashPort, TokenPort tokenPort,
                                   LoginAttemptPort loginAttemptPort, RefreshTokenPort refreshTokenPort) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordHashPort = passwordHashPort;
        this.tokenPort = tokenPort;
        this.loginAttemptPort = loginAttemptPort;
        this.refreshTokenPort = refreshTokenPort;
    }

    @Override
    public LoginResult handle(LoginUserCommand command) {
        if (command == null || command.username() == null || command.password() == null
                || command.username().isEmpty() || command.password().isEmpty()) {
            throw new IllegalArgumentException("LoginUserCommand cannot be null or have values empty");
        }

        String userKey = "user:" + command.username().toLowerCase(Locale.ROOT);
        String ipKey = command.clientIp() != null && !command.clientIp().isBlank()
                ? "ip:" + command.clientIp() : null;

        // Se comprueba antes de validar la contraseña: bloqueado significa bloqueado, aunque acierte.
        if (loginAttemptPort.failureCount(userKey) >= MAX_FAILURES_PER_USER
                || (ipKey != null && loginAttemptPort.failureCount(ipKey) >= MAX_FAILURES_PER_IP)) {
            throw new TooManyAttemptsException(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en "
                            + LOCKOUT_WINDOW.toMinutes() + " minutos.", LOCKOUT_WINDOW);
        }

        User user = userMapper.entityToDto(userRepository.findByUsername(command.username()));
        if (user == null || !passwordHashPort.matches(command.password(), user.getPassword())) {
            loginAttemptPort.recordFailure(userKey, LOCKOUT_WINDOW);
            if (ipKey != null) {
                loginAttemptPort.recordFailure(ipKey, LOCKOUT_WINDOW);
            }
            throw new BadCredentialsException("Invalid username or password");
        }

        loginAttemptPort.clearFailures(userKey);
        return new LoginResult(tokenPort.generateToken(command.username()), tokenPort.accessTokenTtl(),
                refreshTokenPort.issue(command.username()), refreshTokenPort.ttl());
    }

    @Override
    public Class<LoginUserCommand> commandType() {
        return LoginUserCommand.class;
    }
}
