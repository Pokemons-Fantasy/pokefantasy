package com.villu.pokefantasy.commands.users.create;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

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

    @Override
    public Void handle(CreateUserCommand command) {
        if (command == null || command.username() == null || command.password() == null
                || command.username().isEmpty() || command.password().isEmpty()) {
            throw new IllegalArgumentException("CreateUserCommand cannot be null or have values empty");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .name(command.username())
                .password(passwordHashPort.encode(command.password()))
                .build();

        userRepository.saveUser(userMapper.dtoToEntity(user));
        return null;
    }

    @Override
    public Class<CreateUserCommand> commandType() {
        return CreateUserCommand.class;
    }
}
