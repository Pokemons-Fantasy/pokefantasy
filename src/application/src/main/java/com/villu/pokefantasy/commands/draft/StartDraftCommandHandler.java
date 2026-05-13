package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class StartDraftCommandHandler implements CommandHandler<StartDraftCommand, Void> {

    private final DraftRepository draftRepository;

    public StartDraftCommandHandler(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    @Override
    public Void handle(StartDraftCommand command) {
        if (command.turnOrder() == null || command.turnOrder().isEmpty()) {
            throw new IllegalArgumentException("Turn order must have at least one player");
        }

        draftRepository.findActive().ifPresent(d -> {
            throw new IllegalStateException("A draft is already active with status: " + d.getStatus());
        });

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(command.turnOrder());
        draft.setCurrentTurnIndex(0);
        draft.setCurrentRound(1);
        draft.setPicks(new ArrayList<>());

        draftRepository.save(draft);
        return null;
    }

    @Override
    public Class<StartDraftCommand> commandType() {
        return StartDraftCommand.class;
    }
}
