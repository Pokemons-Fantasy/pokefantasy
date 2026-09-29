package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

/** Vuelve a repartir los tiers del pool por BST (porcentajes de la liga) durante la preparación. */
@Service
public class ResetDraftPoolTiersCommandHandler implements CommandHandler<ResetDraftPoolTiersCommand, Void> {

    private final DraftSetupGuard draftSetupGuard;
    private final TierAssignmentService tierAssignmentService;

    public ResetDraftPoolTiersCommandHandler(DraftSetupGuard draftSetupGuard,
                                             TierAssignmentService tierAssignmentService) {
        this.draftSetupGuard = draftSetupGuard;
        this.tierAssignmentService = tierAssignmentService;
    }

    @Override
    public Void handle(ResetDraftPoolTiersCommand command) {
        LeagueEntity league = draftSetupGuard.requireDraftInSetup(command.leagueId(), command.requestingUsername()).league();
        tierAssignmentService.assignTiersToPool(command.leagueId(),
                league.getSettings() != null ? league.getSettings() : LeagueSettings.defaults());
        return null;
    }

    @Override
    public Class<ResetDraftPoolTiersCommand> commandType() {
        return ResetDraftPoolTiersCommand.class;
    }
}
