package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Abre la preparación del draft: crea el draft en PENDING con la configuración por defecto, el orden de
 * turnos de la lista de miembros y los tiers calculados por BST como punto de partida. Cierra las nominaciones.
 */
@Service
public class PrepareDraftCommandHandler implements CommandHandler<PrepareDraftCommand, Void> {

    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final ClosedListRepository closedListRepository;
    private final TierAssignmentService tierAssignmentService;
    private final LeagueRepository leagueRepository;

    public PrepareDraftCommandHandler(DraftRepository draftRepository,
                                      LeagueAdminGuard leagueAdminGuard,
                                      ClosedListRepository closedListRepository,
                                      TierAssignmentService tierAssignmentService,
                                      LeagueRepository leagueRepository) {
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.closedListRepository = closedListRepository;
        this.tierAssignmentService = tierAssignmentService;
        this.leagueRepository = leagueRepository;
    }

    @Override
    public Void handle(PrepareDraftCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        draftRepository.findLatestByLeagueId(command.leagueId()).ifPresent(latest -> {
            switch (latest.getStatus()) {
                case PENDING -> throw new IllegalStateException("El draft ya se está preparando");
                case IN_PROGRESS -> throw new IllegalStateException("Ya hay un draft en marcha en esta liga");
                case COMPLETED -> throw new IllegalStateException("Esta liga ya ha hecho su draft");
                default -> { } // CANCELLED: se puede volver a preparar
            }
        });

        if (closedListRepository.findAllByLeagueId(command.leagueId()).isEmpty()) {
            throw new IllegalStateException("El pool está vacío: nominad Pokémon antes de preparar el draft");
        }

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        draft.setLeagueId(command.leagueId());
        draft.setTurnOrder(league.getMembers().stream().map(LeagueMember::getUsername)
                .collect(Collectors.toCollection(ArrayList::new)));
        draft.setCurrentTurnIndex(0);
        draft.setCurrentRound(1);
        draft.setPicks(new ArrayList<>());
        draft.setDraftHistory(new ArrayList<>());
        draft.setConfig(DraftConfig.defaults());
        draftRepository.save(draft);

        // Sin ajustes guardados, GET settings enseña los de por defecto (20 rondas) pero el draft contaría 10:
        // se guardan ya para que las rondas del draft sean las mismas en el front y en el back.
        if (league.getSettings() == null) {
            league.setSettings(LeagueSettings.defaults());
            leagueRepository.save(league);
        }
        tierAssignmentService.assignTiersToPool(command.leagueId(), league.getSettings());
        return null;
    }

    @Override
    public Class<PrepareDraftCommand> commandType() {
        return PrepareDraftCommand.class;
    }
}
