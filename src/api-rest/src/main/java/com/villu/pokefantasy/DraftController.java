package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.draft.DraftFacade;
import com.villu.pokefantasy.request.draft.DraftPickRequest;
import com.villu.pokefantasy.request.draft.StartDraftRequest;
import com.villu.pokefantasy.response.DraftStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/draft")
public class DraftController {

    private final DraftFacade draftFacade;

    public DraftController(DraftFacade draftFacade) {
        this.draftFacade = draftFacade;
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> startDraft(@RequestBody StartDraftRequest request) throws Exception {
        draftFacade.startDraft(request.getTurnOrder());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pick")
    public ResponseEntity<Void> pick(@AuthenticationPrincipal UserDetails userDetails,
                                     @RequestBody DraftPickRequest request) throws Exception {
        draftFacade.pick(userDetails.getUsername(), request.getPokemonName());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<DraftStatusResponse> getStatus() throws Exception {
        return ResponseEntity.ok(draftFacade.getStatus());
    }
}
