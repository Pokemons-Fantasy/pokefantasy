package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.invite.GenerateInviteLinkResponse;
import com.villu.pokefantasy.commands.invite.InviteFacade;
import com.villu.pokefantasy.commands.invite.RedeemInviteResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class InviteController {

    private final InviteFacade inviteFacade;

    public InviteController(InviteFacade inviteFacade) {
        this.inviteFacade = inviteFacade;
    }

    @PostMapping("/leagues/{leagueId}/invite/generate")
    public ResponseEntity<GenerateInviteLinkResponse> generate(
            @PathVariable String leagueId,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(inviteFacade.generateInvite(leagueId, userDetails.getUsername()));
    }

    @PostMapping("/invite/{token}/redeem")
    public ResponseEntity<RedeemInviteResponse> redeem(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(inviteFacade.redeem(token, userDetails.getUsername()));
    }
}
