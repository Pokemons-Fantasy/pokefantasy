package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.closedlist.ClosedListFacade;
import com.villu.pokefantasy.request.closedlist.AssignTierRequest;
import com.villu.pokefantasy.request.closedlist.NominatePokemonRequest;
import com.villu.pokefantasy.response.ClosedListEntryResponse;
import com.villu.pokefantasy.response.TierAdjustmentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/leagues/{leagueId}/closed-list")
public class ClosedListController {

    private final ClosedListFacade closedListFacade;

    public ClosedListController(ClosedListFacade closedListFacade) {
        this.closedListFacade = closedListFacade;
    }

    @PostMapping("/nominate")
    public ResponseEntity<Void> nominate(@PathVariable String leagueId,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         @RequestBody NominatePokemonRequest request) throws Exception {
        closedListFacade.nominate(userDetails.getUsername(), request.getPokemonName(), leagueId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/nominate/{pokemonName}")
    public ResponseEntity<Void> denominate(@PathVariable String leagueId,
                                           @PathVariable String pokemonName,
                                           @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        closedListFacade.denominate(userDetails.getUsername(), pokemonName, leagueId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{entryId}/tier")
    public ResponseEntity<TierAdjustmentResponse> assignTier(@PathVariable String leagueId,
                                                              @PathVariable String entryId,
                                                              @AuthenticationPrincipal UserDetails userDetails,
                                                              @RequestBody AssignTierRequest request) throws Exception {
        return ResponseEntity.ok(closedListFacade.assignTier(entryId, request.getTier(), leagueId, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<ClosedListEntryResponse>> getClosedList(@PathVariable String leagueId,
                                                                        @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(closedListFacade.getClosedList(leagueId, userDetails.getUsername()));
    }
}
