package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import org.springframework.stereotype.Service;

@Service
public class DenominatePokemonCommandHandler implements CommandHandler<DenominatePokemonCommand, Void> {

    private final ClosedListRepository closedListRepository;
    private final DraftRepository draftRepository;

    public DenominatePokemonCommandHandler(ClosedListRepository closedListRepository,
                                           DraftRepository draftRepository) {
        this.closedListRepository = closedListRepository;
        this.draftRepository = draftRepository;
    }

    @Override
    public Void handle(DenominatePokemonCommand command) {
        draftRepository.findLatest().ifPresent(draft -> {
            if (draft.getStatus() != DraftStatus.PENDING) {
                throw new IllegalStateException("Cannot remove nominations: draft is already " + draft.getStatus());
            }
        });

        closedListRepository.deleteByPokemonNameAndNominatedBy(command.pokemonName(), command.username());
        return null;
    }

    @Override
    public Class<DenominatePokemonCommand> commandType() {
        return DenominatePokemonCommand.class;
    }
}
