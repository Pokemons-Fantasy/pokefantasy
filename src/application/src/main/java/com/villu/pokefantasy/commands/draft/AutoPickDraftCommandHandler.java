package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class AutoPickDraftCommandHandler implements CommandHandler<AutoPickDraftCommand, Void> {

    private final LeagueMembershipGuard leagueMembershipGuard;
    private final DraftTurnTimeoutService draftTurnTimeoutService;

    public AutoPickDraftCommandHandler(LeagueMembershipGuard leagueMembershipGuard,
                                       DraftTurnTimeoutService draftTurnTimeoutService) {
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.draftTurnTimeoutService = draftTurnTimeoutService;
    }

    @Override
    public Void handle(AutoPickDraftCommand command) throws Exception {
        // Cualquier miembro puede disparar el auto-pick (el cliente de quien esté mirando el draft
        // lo lanza al vencer el turno), pero nadie de fuera de la liga.
        LeagueEntity league = leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());
        draftTurnTimeoutService.autoPickExpiredTurn(league);
        return null;
    }

    @Override
    public Class<AutoPickDraftCommand> commandType() {
        return AutoPickDraftCommand.class;
    }
}
