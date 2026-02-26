package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.create.CreateUserCommand;
import com.villu.pokefantasy.commands.users.login.LoginUserCommand;
import com.villu.pokefantasy.mediator.Mediator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Fachada para mantener la "lógica" agrupada como estaba (antes en SaveUser)
 * pero delegando en CommandHandlers.
 */
@Service
public class UserFacade {

    private final Mediator mediator;

    public UserFacade(Mediator mediator) {
        this.mediator = mediator;
    }


    public void create(String username, String password) throws Exception {
        mediator.send(new CreateUserCommand(username, password));
    }

    public boolean login(String username, String password) throws Exception {
        return mediator.send(new LoginUserCommand(username, password));
    }
}

