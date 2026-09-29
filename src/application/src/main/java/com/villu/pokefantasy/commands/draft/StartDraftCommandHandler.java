package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StartDraftCommandHandler implements CommandHandler<StartDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final LeagueRepository leagueRepository;
    private final TierAssignmentService tierAssignmentService;
    private final DraftTurnNotifier draftTurnNotifier;
    private final TurnOrderPolicy turnOrderPolicy;

    public StartDraftCommandHandler(DraftRepository draftRepository,
                                    LeagueAdminGuard leagueAdminGuard,
                                    LeagueRepository leagueRepository,
                                    TierAssignmentService tierAssignmentService,
                                    DraftTurnNotifier draftTurnNotifier,
                                    TurnOrderPolicy turnOrderPolicy) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.leagueRepository = leagueRepository;
        this.tierAssignmentService = tierAssignmentService;
        this.draftTurnNotifier = draftTurnNotifier;
        this.turnOrderPolicy = turnOrderPolicy;
    }

    @Override
    public Void handle(StartDraftCommand command) {
        if (command == null || command.turnOrder() == null || command.turnOrder().isEmpty()) {
            throw new IllegalArgumentException("El orden de turnos debe tener al menos un jugador");
        }

        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());
        draftRepository.findActiveByLeagueId(command.leagueId()).ifPresent(d -> {
            throw new IllegalStateException("Ya hay un draft en marcha en esta liga");
        });

        List<String> turnOrder = turnOrderPolicy.canonical(command.turnOrder(), league);

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(turnOrder);
        draft.setCurrentTurnIndex(0);
        draft.setCurrentRound(1);
        draft.setPicks(new ArrayList<>());
        draft.setLeagueId(command.leagueId());
        draft.setCurrentTurnStartedAt(Instant.now());

        draftRepository.save(draft);
        assignTiersToPool(command.leagueId());
        draftTurnNotifier.notifyCurrentTurn(draft, leagueRepository.findById(command.leagueId()).orElse(null));
        return null;
    }

    private void assignTiersToPool(String leagueId) {
        LeagueSettings settings = leagueRepository.findById(leagueId)
                .map(l -> l.getSettings() != null ? l.getSettings() : LeagueSettings.defaults())
                .orElse(LeagueSettings.defaults());
        tierAssignmentService.assignTiersToPool(leagueId, settings);
    }

    @Override
    public Class<StartDraftCommand> commandType() {
        return StartDraftCommand.class;
    }
}
