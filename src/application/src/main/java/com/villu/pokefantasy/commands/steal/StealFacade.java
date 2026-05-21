package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.mediator.Mediator;
import org.springframework.stereotype.Service;

@Service
public class StealFacade {

    private final Mediator mediator;

    public StealFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public void steal(String leagueId, String stealer, String targetPokemonName) throws Exception {
        mediator.send(new StealPokemonCommand(leagueId, stealer, targetPokemonName));
    }

    public void setStealPrice(String leagueId, String username, String pokemonName, int newPrice) throws Exception {
        mediator.send(new SetStealPriceCommand(leagueId, username, pokemonName, newPrice));
    }
}
