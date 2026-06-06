package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeFacade {

    private final Mediator mediator;

    public TradeFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public String propose(String leagueId, String proposer, String responder,
                           String proposerPokemonName, String responderPokemonName,
                           int coinsOffered) throws Exception {
        return mediator.send(new ProposeTradeCommand(leagueId, proposer, responder,
                proposerPokemonName, responderPokemonName, coinsOffered));
    }

    public List<TradeResponse> getTrades(String leagueId, String username) throws Exception {
        return mediator.send(new GetTradesCommand(leagueId, username));
    }

    public List<TradeResponse> getMyPendingTrades(String username) throws Exception {
        return mediator.send(new GetMyPendingTradesCommand(username));
    }

    public void respond(String leagueId, String tradeId, String respondingUser,
                        boolean accept) throws Exception {
        mediator.send(new RespondToTradeCommand(leagueId, tradeId, respondingUser, accept));
    }

    public void cancel(String leagueId, String tradeId, String requestingUser) throws Exception {
        mediator.send(new CancelTradeCommand(leagueId, tradeId, requestingUser));
    }
}
