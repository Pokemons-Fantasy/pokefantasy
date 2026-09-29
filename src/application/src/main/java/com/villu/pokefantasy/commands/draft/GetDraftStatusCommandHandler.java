package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.response.DraftPickResponse;
import com.villu.pokefantasy.response.DraftStatusResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GetDraftStatusCommandHandler implements CommandHandler<GetDraftStatusCommand, DraftStatusResponse> {

    private final DraftRepository draftRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;
    private final DraftTurnService draftTurnService;

    public GetDraftStatusCommandHandler(DraftRepository draftRepository,
                                        LeagueRepository leagueRepository,
                                        LeagueMembershipGuard leagueMembershipGuard,
                                        DraftTurnService draftTurnService) {
        this.draftRepository = draftRepository;
        this.leagueRepository = leagueRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.draftTurnService = draftTurnService;
    }

    @Override
    public DraftStatusResponse handle(GetDraftStatusCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());

        DraftEntity draft = draftRepository.findActiveByLeagueId(command.leagueId())
                .or(() -> draftRepository.findLatestByLeagueId(command.leagueId()))
                .orElseThrow(() -> new IllegalStateException("Esta liga no tiene draft"));

        // Completado o en preparación: no hay turno.
        String currentTurn = draft.getStatus() == DraftStatus.COMPLETED || draft.getStatus() == DraftStatus.PENDING
                ? null
                : draft.getTurnOrder().get(draft.getCurrentTurnIndex());

        Instant turnDeadline = null;
        if (draft.getStatus() == DraftStatus.IN_PROGRESS && draft.getCurrentTurnStartedAt() != null) {
            LeagueSettings settings = leagueRepository.findById(draft.getLeagueId())
                    .map(l -> l.getSettings() != null ? l.getSettings() : LeagueSettings.defaults())
                    .orElse(LeagueSettings.defaults());
            Integer timer = settings.getTurnTimerSeconds();
            if (timer != null && timer > 0) {
                turnDeadline = draft.getCurrentTurnStartedAt().plusSeconds(timer);
            }
        }

        return DraftStatusResponse.builder()
                .id(draft.getId())
                .status(draft.getStatus())
                .turnOrder(draft.getTurnOrder())
                .currentTurn(currentTurn)
                .currentRound(draft.getCurrentRound())
                .picks(mapPicks(draft.getPicks()))
                .draftHistory(mapPicks(draft.getDraftHistory()))
                .turnDeadline(turnDeadline)
                .config(draft.getConfig())
                .budgets(budgets(draft))
                .build();
    }

    private List<DraftPickResponse> mapPicks(List<DraftPick> picks) {
        return picks == null ? Collections.emptyList() : picks.stream()
                .map(pick -> DraftPickResponse.builder()
                        .username(pick.getUsername())
                        .pokemonName(pick.getPokemonName())
                        .pokemonId(pick.getPokemonId())
                        .round(pick.getRound())
                        .pickedAt(pick.getPickedAt())
                        .customStealPrice(pick.getCustomStealPrice())
                        .lockedUntil(pick.getLockedUntil())
                        .price(pick.getPrice())
                        .build())
                .toList();
    }

    /** Monedas que le quedan a cada jugador, en el orden de turnos; null si el draft no tiene presupuesto. */
    private Map<String, Integer> budgets(DraftEntity draft) {
        if (draft.getConfig() == null) return null;
        Map<String, Integer> budgets = new LinkedHashMap<>();
        draft.getTurnOrder().forEach(u -> budgets.put(u, draftTurnService.remainingBudget(draft, u)));
        return budgets;
    }

    @Override
    public Class<GetDraftStatusCommand> commandType() {
        return GetDraftStatusCommand.class;
    }
}
