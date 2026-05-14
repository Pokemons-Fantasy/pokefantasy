package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.closedlist.ClosedListFacade;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.request.closedlist.AssignTierRequest;
import com.villu.pokefantasy.request.closedlist.NominatePokemonRequest;
import com.villu.pokefantasy.response.ClosedListEntryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/closed-list")
public class ClosedListController {

    private final ClosedListFacade closedListFacade;

    public ClosedListController(ClosedListFacade closedListFacade) {
        this.closedListFacade = closedListFacade;
    }

    @PostMapping("/nominate")
    public ResponseEntity<Void> nominate(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestBody NominatePokemonRequest request) throws Exception {
        closedListFacade.nominate(userDetails.getUsername(), request.getPokemonName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/nominate/{pokemonName}")
    public ResponseEntity<Void> denominate(@AuthenticationPrincipal UserDetails userDetails,
                                           @PathVariable String pokemonName) throws Exception {
        closedListFacade.denominate(userDetails.getUsername(), pokemonName);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{entryId}/tier")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignTier(@PathVariable String entryId,
                                           @RequestBody AssignTierRequest request) throws Exception {
        closedListFacade.assignTier(entryId, request.getTier());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<ClosedListEntryResponse>> getClosedList() throws Exception {
        return ResponseEntity.ok(closedListFacade.getClosedList());
    }
}
