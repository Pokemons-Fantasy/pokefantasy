package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.ClosedListEntryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClosedListFacade {

    private final Mediator mediator;

    public ClosedListFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public void nominate(String username, String pokemonName, String leagueId) throws Exception {
        mediator.send(new NominatePokemonCommand(username, pokemonName, leagueId));
    }

    public void denominate(String username, String pokemonName, String leagueId) throws Exception {
        mediator.send(new DenominatePokemonCommand(username, pokemonName, leagueId));
    }

    public void assignTier(String entryId, Tier tier, String leagueId, String requestingUsername) throws Exception {
        mediator.send(new AssignTierCommand(entryId, tier, leagueId, requestingUsername));
    }

    public List<ClosedListEntryResponse> getClosedList(String leagueId) throws Exception {
        return mediator.send(new GetClosedListCommand(leagueId));
    }
}
