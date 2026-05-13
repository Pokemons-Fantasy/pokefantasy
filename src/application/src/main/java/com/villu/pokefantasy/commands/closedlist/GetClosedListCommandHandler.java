package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.response.ClosedListEntryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetClosedListCommandHandler implements CommandHandler<GetClosedListCommand, List<ClosedListEntryResponse>> {

    private final ClosedListRepository closedListRepository;

    public GetClosedListCommandHandler(ClosedListRepository closedListRepository) {
        this.closedListRepository = closedListRepository;
    }

    @Override
    public List<ClosedListEntryResponse> handle(GetClosedListCommand command) {
        return closedListRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Class<GetClosedListCommand> commandType() {
        return GetClosedListCommand.class;
    }

    private ClosedListEntryResponse toResponse(ClosedListEntity entity) {
        return ClosedListEntryResponse.builder()
                .id(entity.getId())
                .pokemonId(entity.getPokemonId())
                .pokemonName(entity.getPokemonName())
                .tier(entity.getTier())
                .stats(entity.getStats())
                .types(entity.getTypes())
                .nominatedBy(entity.getNominatedBy())
                .build();
    }
}
