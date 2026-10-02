package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StartDraftCommandHandler implements CommandHandler<StartDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final LeagueRepository leagueRepository;
    private final TierAssignmentService tierAssignmentService;
    private final DraftTurnNotifier draftTurnNotifier;
    private final TurnOrderPolicy turnOrderPolicy;
    private final ClosedListRepository closedListRepository;
    private final DraftTurnService draftTurnService;

    public StartDraftCommandHandler(DraftRepository draftRepository,
                                    LeagueAdminGuard leagueAdminGuard,
                                    LeagueRepository leagueRepository,
                                    TierAssignmentService tierAssignmentService,
                                    DraftTurnNotifier draftTurnNotifier,
                                    TurnOrderPolicy turnOrderPolicy,
                                    ClosedListRepository closedListRepository,
                                    DraftTurnService draftTurnService) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.leagueRepository = leagueRepository;
        this.tierAssignmentService = tierAssignmentService;
        this.draftTurnNotifier = draftTurnNotifier;
        this.turnOrderPolicy = turnOrderPolicy;
        this.closedListRepository = closedListRepository;
        this.draftTurnService = draftTurnService;
    }

    @Override
    public Void handle(StartDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("El orden de turnos debe tener al menos un jugador");
        }

        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        Optional<DraftEntity> active = draftRepository.findActiveByLeagueId(command.leagueId());
        if (active.isPresent()) {
            if (active.get().getStatus() == DraftStatus.PENDING) {
                return startPrepared(active.get(), league, command.leagueId());
            }
            throw new IllegalStateException("Ya hay un draft en marcha en esta liga");
        }

        // Sin preparación: el front anterior a la configuración del draft manda el orden y arranca directamente
        // (draft gratis y lineal). Quitar cuando no quede ningún cliente que lo use.
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

    private Void startPrepared(DraftEntity draft, LeagueEntity league, String leagueId) {
        // Alguien pudo entrar o salir de la liga después de guardar el orden de turnos.
        draft.setTurnOrder(turnOrderPolicy.canonical(draft.getTurnOrder(), league));
        List<ClosedListEntity> pool = closedListRepository.findAllByLeagueId(leagueId);
        if (pool.isEmpty()) {
            throw new IllegalStateException("El pool está vacío: nominad Pokémon antes de empezar");
        }
        if (pool.stream().anyMatch(e -> e.getTier() == null)) {
            throw new IllegalStateException("Hay Pokémon sin tier: recalcula los tiers antes de empezar");
        }
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draftTurnService.placeFirstTurn(draft, draftTurnService.available(draft, pool),
                draftTurnService.maxTeamSize(league));
        if (draft.getStatus() == DraftStatus.COMPLETED) {
            throw new IllegalArgumentException("Con este presupuesto nadie puede elegir ningún Pokémon");
        }
        draft.setCurrentTurnStartedAt(Instant.now());
        draftRepository.save(draft);
        draftTurnNotifier.notifyCurrentTurn(draft, league);
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
