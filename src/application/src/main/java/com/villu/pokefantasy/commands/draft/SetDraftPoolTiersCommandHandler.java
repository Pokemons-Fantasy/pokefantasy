package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Mueve Pokémon del pool a otro tier durante la preparación. Sin cascada: el reparto lo decide el admin. */
@Service
public class SetDraftPoolTiersCommandHandler implements CommandHandler<SetDraftPoolTiersCommand, Void> {

    private final DraftSetupGuard draftSetupGuard;
    private final ClosedListRepository closedListRepository;

    public SetDraftPoolTiersCommandHandler(DraftSetupGuard draftSetupGuard, ClosedListRepository closedListRepository) {
        this.draftSetupGuard = draftSetupGuard;
        this.closedListRepository = closedListRepository;
    }

    @Override
    public Void handle(SetDraftPoolTiersCommand command) {
        draftSetupGuard.requireDraftInSetup(command.leagueId(), command.requestingUsername());
        if (command.entryIds() == null || command.entryIds().isEmpty()) {
            throw new IllegalArgumentException("Elige al menos un Pokémon");
        }
        if (command.tier() == null) {
            throw new IllegalArgumentException("Indica el tier");
        }
        List<String> ids = command.entryIds().stream().distinct().toList();
        for (String id : ids) {
            closedListRepository.findById(id)
                    .filter(e -> command.leagueId().equals(e.getLeagueId()))
                    .orElseThrow(() -> new IllegalArgumentException("Ese Pokémon no está en el pool de esta liga"));
        }
        ids.forEach(id -> closedListRepository.updateTier(id, command.tier()));
        return null;
    }

    @Override
    public Class<SetDraftPoolTiersCommand> commandType() {
        return SetDraftPoolTiersCommand.class;
    }
}
