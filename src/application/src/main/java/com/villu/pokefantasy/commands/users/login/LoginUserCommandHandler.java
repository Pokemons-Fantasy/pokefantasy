package com.villu.pokefantasy.commands.users.login;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class LoginUserCommandHandler implements CommandHandler<LoginUserCommand, Boolean> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public LoginUserCommandHandler(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public Boolean handle(LoginUserCommand command) {
        if (command == null || command.username() == null || command.password() == null
                || command.username().isEmpty() || command.password().isEmpty()) {
            throw new IllegalArgumentException("LoginUserCommand cannot be null or have values empty");
        }

        User user = userMapper.entityToDto(userRepository.findByUsername(command.username()));
        if (user == null) {
            return false;
        }

        String encodedPassword = Base64.getEncoder().encodeToString(command.password().getBytes(StandardCharsets.UTF_8));
        return user.getPassword().equals(encodedPassword);
    }

    @Override
    public Class<LoginUserCommand> commandType() {
        return LoginUserCommand.class;
    }
}

