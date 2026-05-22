package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.response.DraftPickResponse;
import com.villu.pokefantasy.response.DraftStatusResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class GetDraftStatusCommandHandler implements CommandHandler<GetDraftStatusCommand, DraftStatusResponse> {

    private final DraftRepository draftRepository;

    public GetDraftStatusCommandHandler(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    @Override
    public DraftStatusResponse handle(GetDraftStatusCommand command) {
        DraftEntity draft = draftRepository.findActiveByLeagueId(command.leagueId())
                .or(() -> draftRepository.findLatestByLeagueId(command.leagueId()))
                .orElseThrow(() -> new IllegalStateException("No draft found for league: " + command.leagueId()));

        String currentTurn = draft.getStatus() == DraftStatus.COMPLETED ? null
                : draft.getTurnOrder().get(draft.getCurrentTurnIndex());

        return DraftStatusResponse.builder()
                .id(draft.getId())
                .status(draft.getStatus())
                .turnOrder(draft.getTurnOrder())
                .currentTurn(currentTurn)
                .currentRound(draft.getCurrentRound())
                .picks(mapPicks(draft.getPicks()))
                .draftHistory(mapPicks(draft.getDraftHistory()))
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
                        .lockedUntilRound(pick.getLockedUntilRound())
                        .build())
                .toList();
    }

    @Override
    public Class<GetDraftStatusCommand> commandType() {
        return GetDraftStatusCommand.class;
    }
}
