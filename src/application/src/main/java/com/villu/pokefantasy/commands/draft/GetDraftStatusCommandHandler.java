package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.response.DraftStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class GetDraftStatusCommandHandler implements CommandHandler<GetDraftStatusCommand, DraftStatusResponse> {

    private final DraftRepository draftRepository;

    public GetDraftStatusCommandHandler(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    @Override
    public DraftStatusResponse handle(GetDraftStatusCommand command) {
        DraftEntity draft = draftRepository.findActive()
                .orElseThrow(() -> new IllegalStateException("No active draft found"));

        String currentTurn = draft.getStatus().name().equals("COMPLETED") ? null
                : draft.getTurnOrder().get(draft.getCurrentTurnIndex());

        return DraftStatusResponse.builder()
                .id(draft.getId())
                .status(draft.getStatus())
                .turnOrder(draft.getTurnOrder())
                .currentTurn(currentTurn)
                .currentRound(draft.getCurrentRound())
                .picks(draft.getPicks())
                .build();
    }

    @Override
    public Class<GetDraftStatusCommand> commandType() {
        return GetDraftStatusCommand.class;
    }
}
