package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.springframework.stereotype.Service;

@Service
public class AssignTierCommandHandler implements CommandHandler<AssignTierCommand, Void> {

    private final ClosedListRepository closedListRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public AssignTierCommandHandler(ClosedListRepository closedListRepository,
                                    LeagueAdminGuard leagueAdminGuard) {
        this.closedListRepository = closedListRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public Void handle(AssignTierCommand command) {
        if (command.entryId() == null || command.tier() == null) {
            throw new IllegalArgumentException("entryId and tier are required");
        }

        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        ClosedListEntity entry = closedListRepository.findById(command.entryId())
                .orElseThrow(() -> new IllegalArgumentException("Closed list entry not found: " + command.entryId()));

        if (!command.leagueId().equals(entry.getLeagueId())) {
            throw new IllegalArgumentException("Entry does not belong to league: " + command.leagueId());
        }

        closedListRepository.updateTier(command.entryId(), command.tier());
        return null;
    }

    @Override
    public Class<AssignTierCommand> commandType() {
        return AssignTierCommand.class;
    }
}
