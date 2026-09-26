package com.villu.pokefantasy.commands.users.password;

import com.villu.pokefantasy.commands.users.PasswordPolicy;
import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.commands.users.login.LoginUserCommandHandler;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.LoginAttemptPort;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Cambia la contraseña del usuario autenticado. Exige la actual (una sesión robada no basta) y cuenta
 * los fallos igual que el login, así que no sirve para probar contraseñas por fuerza bruta. Al cambiarla
 * se cierran todas las sesiones y se devuelve una nueva para quien la ha cambiado.
 */
@Service
public class ChangePasswordCommandHandler implements CommandHandler<ChangePasswordCommand, LoginResult> {

    private final UserRepository userRepository;
    private final PasswordHashPort passwordHashPort;
    private final LoginAttemptPort loginAttemptPort;
    private final RefreshTokenPort refreshTokenPort;
    private final TokenPort tokenPort;

    public ChangePasswordCommandHandler(UserRepository userRepository, PasswordHashPort passwordHashPort,
                                        LoginAttemptPort loginAttemptPort, RefreshTokenPort refreshTokenPort,
                                        TokenPort tokenPort) {
        this.userRepository = userRepository;
        this.passwordHashPort = passwordHashPort;
        this.loginAttemptPort = loginAttemptPort;
        this.refreshTokenPort = refreshTokenPort;
        this.tokenPort = tokenPort;
    }

    @Override
    public LoginResult handle(ChangePasswordCommand command) {
        if (command.currentPassword() == null || command.currentPassword().isEmpty()) {
            throw new IllegalArgumentException("Indica tu contraseña actual.");
        }
        PasswordPolicy.validate(command.newPassword());

        // Misma clave que el login: los fallos aquí y allí suman para el mismo bloqueo.
        String userKey = "user:" + command.username().toLowerCase(Locale.ROOT);
        if (loginAttemptPort.failureCount(userKey) >= LoginUserCommandHandler.MAX_FAILURES_PER_USER) {
            throw new TooManyAttemptsException(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en "
                            + LoginUserCommandHandler.LOCKOUT_WINDOW.toMinutes() + " minutos.",
                    LoginUserCommandHandler.LOCKOUT_WINDOW);
        }

        UserEntity user = userRepository.findByUsername(command.username());
        if (user == null || !passwordHashPort.matches(command.currentPassword(), user.getPassword())) {
            loginAttemptPort.recordFailure(userKey, LoginUserCommandHandler.LOCKOUT_WINDOW);
            throw new BadCredentialsException("La contraseña actual no es correcta.");
        }
        if (passwordHashPort.matches(command.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña nueva debe ser distinta de la actual.");
        }

        user.setPassword(passwordHashPort.encode(command.newPassword()));
        userRepository.saveUser(user);
        loginAttemptPort.clearFailures(userKey);

        // Si alguien más tenía la sesión abierta (p. ej. la contraseña se filtró), la pierde; el JWT de
        // acceso que ya tenga caduca solo en ≤ 15 min.
        refreshTokenPort.revokeAll(command.username());
        return new LoginResult(tokenPort.generateToken(command.username()), tokenPort.accessTokenTtl(),
                refreshTokenPort.issue(command.username()), refreshTokenPort.ttl());
    }

    @Override
    public Class<ChangePasswordCommand> commandType() {
        return ChangePasswordCommand.class;
    }
}
