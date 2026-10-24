package com.villu.pokefantasy.commands.users.showdown;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UnlinkShowdownCommandHandler implements CommandHandler<UnlinkShowdownCommand, Void> {

    private final UserRepository userRepository;

    public UnlinkShowdownCommandHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(UnlinkShowdownCommand command) {
        userRepository.clearShowdownAccount(command.username());
        return null;
    }

    @Override
    public Class<UnlinkShowdownCommand> commandType() {
        return UnlinkShowdownCommand.class;
    }
}
