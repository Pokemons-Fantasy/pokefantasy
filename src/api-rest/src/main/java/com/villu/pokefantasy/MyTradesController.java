package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.trade.TradeFacade;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints de trades agregados por usuario (no por liga). */
@RestController
@RequestMapping("/v1/user/trades")
public class MyTradesController {

    private final TradeFacade tradeFacade;

    public MyTradesController(TradeFacade tradeFacade) {
        this.tradeFacade = tradeFacade;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<TradeResponse>> myPendingTrades(
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(tradeFacade.getMyPendingTrades(userDetails.getUsername()));
    }
}
