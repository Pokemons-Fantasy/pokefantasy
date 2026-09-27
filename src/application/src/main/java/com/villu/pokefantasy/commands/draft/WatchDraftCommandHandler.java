package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import org.springframework.stereotype.Service;

@Service
public class WatchDraftCommandHandler implements CommandHandler<WatchDraftCommand, Void> {

    private final LeagueMembershipGuard leagueMembershipGuard;

    public WatchDraftCommandHandler(LeagueMembershipGuard leagueMembershipGuard) {
        this.leagueMembershipGuard = leagueMembershipGuard;
    }

    @Override
    public Void handle(WatchDraftCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());
        return null;
    }

    @Override
    public Class<WatchDraftCommand> commandType() {
        return WatchDraftCommand.class;
    }
}
