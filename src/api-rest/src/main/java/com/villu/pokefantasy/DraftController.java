package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.draft.DraftFacade;
import com.villu.pokefantasy.request.draft.DraftPickRequest;
import com.villu.pokefantasy.request.draft.StartDraftRequest;
import com.villu.pokefantasy.response.DraftStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/leagues/{leagueId}/draft")
public class DraftController {

    private final DraftFacade draftFacade;

    public DraftController(DraftFacade draftFacade) {
        this.draftFacade = draftFacade;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startDraft(@PathVariable String leagueId,
                                           @AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody StartDraftRequest request) throws Exception {
        draftFacade.startDraft(request.getTurnOrder(), leagueId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pick")
    public ResponseEntity<Void> pick(@PathVariable String leagueId,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     @RequestBody DraftPickRequest request) throws Exception {
        draftFacade.pick(userDetails.getUsername(), request.getPokemonName(), leagueId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<DraftStatusResponse> getStatus(@PathVariable String leagueId) throws Exception {
        return ResponseEntity.ok(draftFacade.getStatus(leagueId));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancelDraft(@PathVariable String leagueId,
                                            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        draftFacade.cancelDraft(leagueId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/auto-pick")
    public ResponseEntity<Void> autoPick(@PathVariable String leagueId) throws Exception {
        draftFacade.autoPick(leagueId);
        return ResponseEntity.ok().build();
    }
}
