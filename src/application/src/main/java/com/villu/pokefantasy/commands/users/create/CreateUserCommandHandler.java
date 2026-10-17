package com.villu.pokefantasy.commands.users.create;

import com.villu.pokefantasy.commands.users.PasswordPolicy;
import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.LoginAttemptPort;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, Void> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordHashPort passwordHashPort;
    private final LoginAttemptPort loginAttemptPort;

    public CreateUserCommandHandler(UserRepository userRepository, UserMapper userMapper,
                                    PasswordHashPort passwordHashPort, LoginAttemptPort loginAttemptPort) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordHashPort = passwordHashPort;
        this.loginAttemptPort = loginAttemptPort;
    }

    /** 3-20 caracteres: letras, números, guion y guion bajo (sin espacios ni caracteres raros). */
    static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,20}$");

    /** Cuentas que se pueden crear desde una IP por ventana: frena llenar la base de datos con un script. */
    static final int MAX_REGISTRATIONS_PER_IP = 3;
    static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

    @Override
    public Void handle(CreateUserCommand command) {
        if (command == null || command.username() == null || command.password() == null
                || command.username().isEmpty() || command.password().isEmpty()) {
            throw new IllegalArgumentException("Indica usuario y contraseña.");
        }
        // Solo cuentan los registros correctos (los fallidos no ocupan sitio). Requiere una IP que el
        // cliente no pueda falsear (ClientIpFilter); sin IP no se limita.
        String ipKey = command.clientIp() == null || command.clientIp().isBlank()
                ? null : "register-ip:" + command.clientIp();
        if (ipKey != null && loginAttemptPort.failureCount(ipKey) >= MAX_REGISTRATIONS_PER_IP) {
            throw new TooManyAttemptsException(
                    "Demasiados registros desde esta conexión. Vuelve a intentarlo en "
                            + REGISTRATION_WINDOW.toMinutes() + " minutos.", REGISTRATION_WINDOW);
        }
        if (!USERNAME_PATTERN.matcher(command.username()).matches()) {
            throw new IllegalArgumentException(
                    "El nombre de usuario debe tener entre 3 y 20 caracteres: letras, números, '_' o '-'.");
        }
        PasswordPolicy.validate(command.password());
        // Antes lo detectaba el índice único y al usuario le llegaba el texto de Mongo. Sin distinguir
        // mayúsculas: "lobon" no puede registrarse si ya existe "Lobon" (suplantación visual).
        if (userRepository.existsByUsernameIgnoreCase(command.username())) {
            throw new IllegalStateException("Ese nombre de usuario ya está en uso.");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .name(command.username())
                .password(passwordHashPort.encode(command.password()))
                .role(Role.USER)
                .build();

        userRepository.saveUser(userMapper.dtoToEntity(user));
        if (ipKey != null) {
            loginAttemptPort.recordFailure(ipKey, REGISTRATION_WINDOW);
        }
        return null;
    }

    @Override
    public Class<CreateUserCommand> commandType() {
        return CreateUserCommand.class;
    }
}
