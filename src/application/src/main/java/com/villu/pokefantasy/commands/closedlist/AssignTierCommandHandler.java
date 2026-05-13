package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.springframework.stereotype.Service;

@Service
public class AssignTierCommandHandler implements CommandHandler<AssignTierCommand, Void> {

    private final ClosedListRepository closedListRepository;

    public AssignTierCommandHandler(ClosedListRepository closedListRepository) {
        this.closedListRepository = closedListRepository;
    }

    @Override
    public Void handle(AssignTierCommand command) {
        if (command.entryId() == null || command.tier() == null) {
            throw new IllegalArgumentException("entryId and tier are required");
        }

        closedListRepository.findById(command.entryId())
                .orElseThrow(() -> new IllegalArgumentException("Closed list entry not found: " + command.entryId()));

        closedListRepository.updateTier(command.entryId(), command.tier());
        return null;
    }

    @Override
    public Class<AssignTierCommand> commandType() {
        return AssignTierCommand.class;
    }
}
