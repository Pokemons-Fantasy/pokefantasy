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
    /** Cuánto se recuerda una IP desde la que el usuario entró bien. */
    static final Duration TRUSTED_IP_TTL = Duration.ofDays(30);

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
            throw new IllegalArgumentException("Indica usuario y contraseña.");
        }

        String userKey = "user:" + command.username().toLowerCase(Locale.ROOT);
        boolean hasIp = command.clientIp() != null && !command.clientIp().isBlank();
        String ipKey = hasIp ? "ip:" + command.clientIp() : null;
        String trustedKey = hasIp ? userKey + "|" + command.clientIp() : null;
        // Desde una IP desde la que ya entró, el bloqueo por usuario no aplica ni cuenta sus fallos: si no,
        // cualquiera que conozca su nombre (sale en las ligas) le dejaría sin entrar con 5 intentos.
        // El límite por IP sigue. Requiere una IP que el cliente no pueda falsear (ClientIpFilter).
        boolean knownIp = trustedKey != null && loginAttemptPort.isTrusted(trustedKey);

        // Se comprueba antes de validar la contraseña: bloqueado significa bloqueado, aunque acierte.
        if ((!knownIp && loginAttemptPort.failureCount(userKey) >= MAX_FAILURES_PER_USER)
                || (ipKey != null && loginAttemptPort.failureCount(ipKey) >= MAX_FAILURES_PER_IP)) {
            throw new TooManyAttemptsException(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en "
                            + LOCKOUT_WINDOW.toMinutes() + " minutos.", LOCKOUT_WINDOW);
        }

        User user = userMapper.entityToDto(userRepository.findByUsername(command.username()));
        if (user == null || !passwordHashPort.matches(command.password(), user.getPassword())) {
            if (!knownIp) {
                loginAttemptPort.recordFailure(userKey, LOCKOUT_WINDOW);
            }
            if (ipKey != null) {
                loginAttemptPort.recordFailure(ipKey, LOCKOUT_WINDOW);
            }
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        loginAttemptPort.clearFailures(userKey);
        if (trustedKey != null) {
            loginAttemptPort.markTrusted(trustedKey, TRUSTED_IP_TTL);
        }
        return new LoginResult(tokenPort.generateToken(command.username()), tokenPort.accessTokenTtl(),
                refreshTokenPort.issue(command.username()), refreshTokenPort.ttl());
    }

    @Override
    public Class<LoginUserCommand> commandType() {
        return LoginUserCommand.class;
    }
}
