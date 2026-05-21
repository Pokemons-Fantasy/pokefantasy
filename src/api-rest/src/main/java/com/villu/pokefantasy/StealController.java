package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.steal.StealFacade;
import com.villu.pokefantasy.request.steal.SetStealPriceRequest;
import com.villu.pokefantasy.request.steal.StealPokemonRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/leagues/{leagueId}")
public class StealController {

    private final StealFacade stealFacade;

    public StealController(StealFacade stealFacade) {
        this.stealFacade = stealFacade;
    }

    @PostMapping("/steal")
    public ResponseEntity<Void> steal(@PathVariable String leagueId,
                                      @AuthenticationPrincipal UserDetails userDetails,
                                      @RequestBody StealPokemonRequest request) throws Exception {
        stealFacade.steal(leagueId, userDetails.getUsername(), request.getTargetPokemonName());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/steal-price")
    public ResponseEntity<Void> setStealPrice(@PathVariable String leagueId,
                                              @AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody SetStealPriceRequest request) throws Exception {
        stealFacade.setStealPrice(leagueId, userDetails.getUsername(), request.getPokemonName(), request.getNewPrice());
        return ResponseEntity.ok().build();
    }
}
