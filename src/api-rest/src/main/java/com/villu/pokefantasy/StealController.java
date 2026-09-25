package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.steal.StealFacade;
import com.villu.pokefantasy.request.steal.SetStealPriceRequest;
import com.villu.pokefantasy.request.steal.StealPokemonRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/v1/leagues/{leagueId}")
public class StealController {

    private final StealFacade stealFacade;
    private final RealtimeNotifier realtimeNotifier;
    private final ObjectMapper objectMapper;

    public StealController(StealFacade stealFacade, RealtimeNotifier realtimeNotifier, ObjectMapper redisObjectMapper) {
        this.stealFacade = stealFacade;
        this.realtimeNotifier = realtimeNotifier;
        this.objectMapper = redisObjectMapper;
    }

    @PostMapping("/steal")
    public ResponseEntity<Void> steal(@PathVariable String leagueId,
                                      @AuthenticationPrincipal UserDetails userDetails,
                                      @RequestBody StealPokemonRequest request) throws Exception {
        String victim = stealFacade.steal(leagueId, userDetails.getUsername(), request.getTargetPokemonName());
        String payload = objectMapper.createObjectNode()
                .put("leagueId", leagueId)
                .put("actorUsername", userDetails.getUsername())
                .put("pokemonName", request.getTargetPokemonName())
                .toString();
        realtimeNotifier.notifyUser(victim, "steal", payload);
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
