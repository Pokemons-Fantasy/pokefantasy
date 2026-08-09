package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.BenchEntryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BenchFacade {

    private final Mediator mediator;

    public BenchFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public List<BenchEntryResponse> getBench(String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new GetBenchCommand(leagueId, requestingUsername));
    }

    public void swap(String leagueId, String username, String pokemonToGive, String pokemonToTake) throws Exception {
        mediator.send(new SwapWithBenchCommand(leagueId, username, pokemonToGive, pokemonToTake));
    }

    public void buy(String leagueId, String username, String pokemonName) throws Exception {
        mediator.send(new BuyFromBenchCommand(leagueId, username, pokemonName));
    }

    public void release(String leagueId, String username, String pokemonName) throws Exception {
        mediator.send(new ReleasePokemonCommand(leagueId, username, pokemonName));
    }
}
