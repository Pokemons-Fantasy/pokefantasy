package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.response.TierAdjustmentResponse;
import com.villu.pokefantasy.response.TierChangeDto;
import org.springframework.stereotype.Service;

import java.time.Instant;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ajuste manual de tiers con el draft terminado: los Pokémon elegidos (todos del mismo tier) pasan al tier destino
 * y nadie más se mueve, así que los conteos por tier pueden cambiar.
 */
@Service
public class AssignTierCommandHandler implements CommandHandler<AssignTierCommand, TierAdjustmentResponse> {

    private final ClosedListRepository closedListRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final ActivityEventRepository activityEventRepository;
    private final DraftRepository draftRepository;

    public AssignTierCommandHandler(ClosedListRepository closedListRepository,
                                    LeagueAdminGuard leagueAdminGuard,
                                    ActivityEventRepository activityEventRepository,
                                    DraftRepository draftRepository) {
        this.closedListRepository = closedListRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.activityEventRepository = activityEventRepository;
        this.draftRepository = draftRepository;
    }

    @Override
    public TierAdjustmentResponse handle(AssignTierCommand command) {
        if (command.entryIds() == null || command.entryIds().isEmpty() || command.tier() == null) {
            throw new IllegalArgumentException("Indica los Pokémon y el tier.");
        }

        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        // En preparación y durante el draft el tier fija el precio de cada pick: se mueve desde la preparación
        // y no se toca con el draft en curso.
        draftRepository.findLatestByLeagueId(command.leagueId())
                .filter(d -> d.getStatus() == DraftStatus.PENDING || d.getStatus() == DraftStatus.IN_PROGRESS)
                .ifPresent(d -> {
                    throw new IllegalStateException("Los tiers solo se ajustan aquí con el draft terminado");
                });

        Set<String> targetIds = new HashSet<>(command.entryIds());
        List<ClosedListEntity> targets = closedListRepository.findAllByLeagueId(command.leagueId()).stream()
                .filter(e -> targetIds.contains(e.getId()))
                .toList();

        if (targets.size() != targetIds.size()) {
            throw new IllegalArgumentException("Ese Pokémon no está en el pool de esta liga");
        }

        Tier fromTier = targets.get(0).getTier();
        Tier toTier   = command.tier();

        if (targets.stream().anyMatch(e -> e.getTier() != fromTier)) {
            throw new IllegalArgumentException("Los Pokémon elegidos tienen que estar en el mismo tier");
        }

        if (fromTier == toTier) {
            return TierAdjustmentResponse.builder().changes(List.of()).build();
        }

        Instant now = Instant.now();
        for (ClosedListEntity target : targets) {
            closedListRepository.updateTier(target.getId(), toTier);
            activityEventRepository.save(ActivityEventEntity.builder()
                    .leagueId(command.leagueId())
                    .type(ActivityEventType.TIER_CHANGE)
                    .actorUsername(command.requestingUsername())
                    .pokemonName(target.getPokemonName())
                    .fromTier(fromTier.name())
                    .toTier(toTier.name())
                    .createdAt(now)
                    .build());
        }

        List<TierChangeDto> changes = targets.stream()
                .map(t -> TierChangeDto.builder()
                        .pokemonId(t.getPokemonId())
                        .pokemonName(t.getPokemonName())
                        .oldTier(fromTier)
                        .newTier(toTier)
                        .build())
                .toList();
        return TierAdjustmentResponse.builder().changes(changes).build();
    }

    @Override
    public Class<AssignTierCommand> commandType() {
        return AssignTierCommand.class;
    }
}
