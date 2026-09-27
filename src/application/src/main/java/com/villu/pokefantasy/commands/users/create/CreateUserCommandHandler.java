package com.villu.pokefantasy.commands.users.create;

import com.villu.pokefantasy.commands.users.PasswordPolicy;
import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, Void> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordHashPort passwordHashPort;

    public CreateUserCommandHandler(UserRepository userRepository, UserMapper userMapper, PasswordHashPort passwordHashPort) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordHashPort = passwordHashPort;
    }

    /** 3-20 caracteres: letras, números, guion y guion bajo (sin espacios ni caracteres raros). */
    static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,20}$");

    @Override
    public Void handle(CreateUserCommand command) {
        if (command == null || command.username() == null || command.password() == null
                || command.username().isEmpty() || command.password().isEmpty()) {
            throw new IllegalArgumentException("CreateUserCommand cannot be null or have values empty");
        }
        if (!USERNAME_PATTERN.matcher(command.username()).matches()) {
            throw new IllegalArgumentException(
                    "El nombre de usuario debe tener entre 3 y 20 caracteres: letras, números, '_' o '-'.");
        }
        PasswordPolicy.validate(command.password());

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .name(command.username())
                .password(passwordHashPort.encode(command.password()))
                .role(Role.USER)
                .build();

        userRepository.saveUser(userMapper.dtoToEntity(user));
        return null;
    }

    @Override
    public Class<CreateUserCommand> commandType() {
        return CreateUserCommand.class;
    }
}
