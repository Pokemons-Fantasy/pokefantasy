package com.villu.pokefantasy.commands.users;

import com.villu.pokefantasy.commands.users.add.pokemon.user.AddPokemonUserCommand;
import com.villu.pokefantasy.commands.users.create.CreateUserCommand;
import com.villu.pokefantasy.commands.users.login.LoginUserCommand;
import com.villu.pokefantasy.commands.users.search.SearchUsersCommand;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mediator.Mediator;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public String login(String username, String password) throws Exception {
        return mediator.send(new LoginUserCommand(username, password));
    }

    public void addPokemonsToUser(String username, List<Pokemons> pokemons) throws Exception {
        mediator.send(new AddPokemonUserCommand(pokemons, username));
    }

    public List<String> searchUsers(String prefix, String leagueId) throws Exception {
        return mediator.send(new SearchUsersCommand(prefix, leagueId));
    }
}

