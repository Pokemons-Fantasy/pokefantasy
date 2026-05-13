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

    public void nominate(String username, String pokemonName) throws Exception {
        mediator.send(new NominatePokemonCommand(username, pokemonName));
    }

    public void assignTier(String entryId, Tier tier) throws Exception {
        mediator.send(new AssignTierCommand(entryId, tier));
    }

    public List<ClosedListEntryResponse> getClosedList() throws Exception {
        return mediator.send(new GetClosedListCommand());
    }
}
