package com.villu.pokefantasy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.villu.pokefantasy.commands.trade.TradeFacade;
import com.villu.pokefantasy.request.trade.ProposeTradeRequest;
import com.villu.pokefantasy.request.trade.RespondToTradeRequest;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/leagues/{leagueId}")
public class TradeController {

    private final TradeFacade tradeFacade;
    private final UserSseEmitterRegistry userSseRegistry;

    public TradeController(TradeFacade tradeFacade, UserSseEmitterRegistry userSseRegistry) {
        this.tradeFacade = tradeFacade;
        this.userSseRegistry = userSseRegistry;
    }

    @PostMapping("/trades")
    public ResponseEntity<Void> propose(@PathVariable String leagueId,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        @RequestBody ProposeTradeRequest request) throws Exception {
        int coins = request.getCoinsOffered() != null ? request.getCoinsOffered() : 0;
        String tradeId = tradeFacade.propose(leagueId, userDetails.getUsername(), request.getResponder(),
                request.getProposerPokemonName(), request.getResponderPokemonName(), coins);
        String payload = new ObjectMapper().createObjectNode()
                .put("leagueId", leagueId)
                .put("proposer", userDetails.getUsername())
                .put("tradeId", tradeId)
                .toString();
        userSseRegistry.sendToUser(request.getResponder(), "trade-proposed", payload);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeResponse>> getTrades(
            @PathVariable String leagueId,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(tradeFacade.getTrades(leagueId, userDetails.getUsername()));
    }

    @PostMapping("/trades/{tradeId}/respond")
    public ResponseEntity<Void> respond(@PathVariable String leagueId,
                                        @PathVariable String tradeId,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        @RequestBody RespondToTradeRequest request) throws Exception {
        tradeFacade.respond(leagueId, tradeId, userDetails.getUsername(), request.isAccept());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/trades/{tradeId}")
    public ResponseEntity<Void> cancel(@PathVariable String leagueId,
                                       @PathVariable String tradeId,
                                       @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        tradeFacade.cancel(leagueId, tradeId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
