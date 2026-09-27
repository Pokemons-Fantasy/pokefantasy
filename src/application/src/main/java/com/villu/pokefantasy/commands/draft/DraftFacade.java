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

    public void startDraft(List<String> turnOrder, String leagueId, String requestingUsername) throws Exception {
        mediator.send(new StartDraftCommand(turnOrder, leagueId, requestingUsername));
    }

    public void pick(String username, String pokemonName, String leagueId) throws Exception {
        mediator.send(new DraftPickCommand(username, pokemonName, leagueId));
    }

    public DraftStatusResponse getStatus(String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new GetDraftStatusCommand(leagueId, requestingUsername));
    }

    public void cancelDraft(String leagueId, String requestingUsername) throws Exception {
        mediator.send(new CancelDraftCommand(leagueId, requestingUsername));
    }

    public void autoPick(String leagueId, String requestingUsername) throws Exception {
        mediator.send(new AutoPickDraftCommand(leagueId, requestingUsername));
    }

    /** Lanza {@code ForbiddenOperationException} si el usuario no puede seguir el draft de la liga. */
    public void requireDraftWatcher(String leagueId, String requestingUsername) throws Exception {
        mediator.send(new WatchDraftCommand(leagueId, requestingUsername));
    }

    /** Ligas cuyo turno de draft ha vencido (para el job del servidor). */
    public List<String> expiredTurnLeagueIds() throws Exception {
        return mediator.send(new ListExpiredDraftTurnsCommand());
    }

    /** Auto-pick del turno vencido sin usuario que lo pida (job del servidor). */
    public void expireTurn(String leagueId) throws Exception {
        mediator.send(new ExpireDraftTurnCommand(leagueId));
    }
}
