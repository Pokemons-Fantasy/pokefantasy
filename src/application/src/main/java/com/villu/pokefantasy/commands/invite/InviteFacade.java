package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.mediator.Mediator;
import org.springframework.stereotype.Service;

@Service
public class InviteFacade {

    private final Mediator mediator;

    public InviteFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public GenerateInviteLinkResponse generateInvite(String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new GenerateInviteLinkCommand(leagueId, requestingUsername));
    }

    public String redeem(String token, String username) throws Exception {
        return mediator.send(new RedeemInviteCommand(token, username));
    }
}
