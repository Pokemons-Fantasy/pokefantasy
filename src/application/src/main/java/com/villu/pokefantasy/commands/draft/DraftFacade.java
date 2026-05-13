package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.DraftStatusResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DraftFacade {

    private final Mediator mediator;

    public DraftFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public void startDraft(List<String> turnOrder) throws Exception {
        mediator.send(new StartDraftCommand(turnOrder));
    }

    public void pick(String username, String pokemonName) throws Exception {
        mediator.send(new DraftPickCommand(username, pokemonName));
    }

    public DraftStatusResponse getStatus() throws Exception {
        return mediator.send(new GetDraftStatusCommand());
    }
}
