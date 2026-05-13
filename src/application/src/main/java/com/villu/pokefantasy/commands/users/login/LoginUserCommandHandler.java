package com.villu.pokefantasy.commands.users.login;

import com.villu.pokefantasy.dto.users.User;
import com.villu.pokefantasy.mapper.UserMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class LoginUserCommandHandler implements CommandHandler<LoginUserCommand, String> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordHashPort passwordHashPort;
    private final TokenPort tokenPort;

    public LoginUserCommandHandler(UserRepository userRepository, UserMapper userMapper,
                                   PasswordHashPort passwordHashPort, TokenPort tokenPort) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordHashPort = passwordHashPort;
        this.tokenPort = tokenPort;
    }

    @Override
    public String handle(LoginUserCommand command) {
        if (command == null || command.username() == null || command.password() == null
                || command.username().isEmpty() || command.password().isEmpty()) {
            throw new IllegalArgumentException("LoginUserCommand cannot be null or have values empty");
        }

        User user = userMapper.entityToDto(userRepository.findByUsername(command.username()));
        if (user == null) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!passwordHashPort.matches(command.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return tokenPort.generateToken(command.username());
    }

    @Override
    public Class<LoginUserCommand> commandType() {
        return LoginUserCommand.class;
    }
}
