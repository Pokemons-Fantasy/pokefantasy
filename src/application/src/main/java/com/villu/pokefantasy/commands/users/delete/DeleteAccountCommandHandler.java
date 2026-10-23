package com.villu.pokefantasy.commands.users.delete;

import com.villu.pokefantasy.commands.users.login.LoginUserCommandHandler;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.LoginAttemptPort;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** El usuario borra su propia cuenta: comprueba la contraseña y delega en {@link AccountDeletionService}. */
@Service
public class DeleteAccountCommandHandler implements CommandHandler<DeleteAccountCommand, List<String>> {

    private final UserRepository userRepository;
    private final PasswordHashPort passwordHashPort;
    private final LoginAttemptPort loginAttemptPort;
    private final AccountDeletionService accountDeletionService;

    public DeleteAccountCommandHandler(UserRepository userRepository, PasswordHashPort passwordHashPort,
                                       LoginAttemptPort loginAttemptPort,
                                       AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.passwordHashPort = passwordHashPort;
        this.loginAttemptPort = loginAttemptPort;
        this.accountDeletionService = accountDeletionService;
    }

    @Override
    public List<String> handle(DeleteAccountCommand command) {
        if (command.password() == null || command.password().isBlank()) {
            throw new IllegalArgumentException("Indica tu contraseña.");
        }
        checkPassword(command.username(), command.password());
        return accountDeletionService.delete(command.username());
    }

    /** Mismo límite que el login y el cambio de contraseña: los fallos suman para el mismo bloqueo. */
    private void checkPassword(String username, String password) {
        String userKey = "user:" + username.toLowerCase(Locale.ROOT);
        if (loginAttemptPort.failureCount(userKey) >= LoginUserCommandHandler.MAX_FAILURES_PER_USER) {
            throw new TooManyAttemptsException(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en "
                            + LoginUserCommandHandler.LOCKOUT_WINDOW.toMinutes() + " minutos.",
                    LoginUserCommandHandler.LOCKOUT_WINDOW);
        }
        UserEntity user = userRepository.findByUsername(username);
        if (user == null || !passwordHashPort.matches(password, user.getPassword())) {
            loginAttemptPort.recordFailure(userKey, LoginUserCommandHandler.LOCKOUT_WINDOW);
            throw new BadCredentialsException("La contraseña no es correcta.");
        }
        loginAttemptPort.clearFailures(userKey);
    }

    @Override
    public Class<DeleteAccountCommand> commandType() {
        return DeleteAccountCommand.class;
    }
}
