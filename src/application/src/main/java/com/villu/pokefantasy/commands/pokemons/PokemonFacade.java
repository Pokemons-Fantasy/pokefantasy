package com.villu.pokefantasy.commands.pokemons;


import com.villu.pokefantasy.commands.pokemons.available.GetAvailablePokemonsCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.AvailablePokemonResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PokemonFacade {

    private final Mediator mediator;

    public PokemonFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public List<AvailablePokemonResponse> getAvailablePokemons() throws Exception {
        return mediator.send(new GetAvailablePokemonsCommand());
    }
}
