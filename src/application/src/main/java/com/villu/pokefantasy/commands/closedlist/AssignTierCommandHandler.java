package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.response.TierAdjustmentResponse;
import com.villu.pokefantasy.response.TierChangeDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssignTierCommandHandler implements CommandHandler<AssignTierCommand, TierAdjustmentResponse> {

    private final ClosedListRepository closedListRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public AssignTierCommandHandler(ClosedListRepository closedListRepository,
                                    LeagueAdminGuard leagueAdminGuard) {
        this.closedListRepository = closedListRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public TierAdjustmentResponse handle(AssignTierCommand command) {
        if (command.entryId() == null || command.tier() == null) {
            throw new IllegalArgumentException("entryId and tier are required");
        }

        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        ClosedListEntity entry = closedListRepository.findById(command.entryId())
                .orElseThrow(() -> new IllegalArgumentException("Closed list entry not found: " + command.entryId()));

        if (!command.leagueId().equals(entry.getLeagueId())) {
            throw new IllegalArgumentException("Entry does not belong to league: " + command.leagueId());
        }

        Tier fromTier = entry.getTier();
        Tier toTier   = command.tier();

        if (fromTier == toTier) {
            return TierAdjustmentResponse.builder().changes(List.of()).build();
        }

        List<ClosedListEntity> pool = closedListRepository.findAllByLeagueId(command.leagueId());

        List<TierChangeDto> changes = computeCascade(entry, toTier, pool);

        for (TierChangeDto change : changes) {
            closedListRepository.updateTier(
                    pool.stream()
                        .filter(e -> e.getPokemonId() == change.getPokemonId())
                        .findFirst()
                        .map(ClosedListEntity::getId)
                        .orElseThrow(),
                    change.getNewTier());
        }

        return TierAdjustmentResponse.builder().changes(changes).build();
    }

    @Override
    public Class<AssignTierCommand> commandType() {
        return AssignTierCommand.class;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int bst(ClosedListEntity e) {
        if (e.getStats() == null) return 0;
        return e.getStats().stream().mapToInt(s -> s.getBaseStat()).sum();
    }

    private int rank(Tier t) {
        return switch (t) {
            case S -> 0;
            case A -> 1;
            case B -> 2;
            case C -> 3;
            case D -> 4;
        };
    }

    private Tier tierAt(int rank) {
        return switch (rank) {
            case 0 -> Tier.S;
            case 1 -> Tier.A;
            case 2 -> Tier.B;
            case 3 -> Tier.C;
            default -> Tier.D;
        };
    }

    /**
     * Computes the full list of tier changes needed to move {@code target} to {@code toTier},
     * cascading victims one step to keep tier counts balanced.
     *
     * Promotion (toRank < fromRank): for each tier T from toRank to fromRank-1,
     *   pick the lowest-BST pokemon in T (not the target) and bump it down one tier.
     *
     * Demotion (toRank > fromRank): for each tier T from toRank down to fromRank+1,
     *   pick the highest-BST pokemon in T (not the target) and bump it up one tier.
     */
    private List<TierChangeDto> computeCascade(ClosedListEntity target, Tier toTier, List<ClosedListEntity> pool) {
        int fromRank = rank(target.getTier());
        int toRank   = rank(toTier);

        List<TierChangeDto> changes = new ArrayList<>();

        if (toRank < fromRank) {
            // Promotion: iterate from destination tier down to just before origin tier
            for (int r = toRank; r < fromRank; r++) {
                final int currentRank = r;
                ClosedListEntity victim = pool.stream()
                        .filter(e -> e.getTier() != null && rank(e.getTier()) == currentRank)
                        .filter(e -> !e.getId().equals(target.getId()))
                        .min(Comparator.comparingInt(this::bst)
                                .thenComparing(ClosedListEntity::getPokemonName))
                        .orElse(null);

                if (victim != null) {
                    Tier victimNewTier = tierAt(currentRank + 1);
                    changes.add(TierChangeDto.builder()
                            .pokemonId(victim.getPokemonId())
                            .pokemonName(victim.getPokemonName())
                            .oldTier(victim.getTier())
                            .newTier(victimNewTier)
                            .build());
                    // Update in-memory so subsequent iterations see the new state
                    victim.setTier(victimNewTier);
                }
            }
        } else {
            // Demotion: iterate from destination tier up to just after origin tier
            for (int r = toRank; r > fromRank; r--) {
                final int currentRank = r;
                ClosedListEntity victim = pool.stream()
                        .filter(e -> e.getTier() != null && rank(e.getTier()) == currentRank)
                        .filter(e -> !e.getId().equals(target.getId()))
                        .max(Comparator.comparingInt(this::bst)
                                .thenComparing(Comparator.comparing(ClosedListEntity::getPokemonName).reversed()))
                        .orElse(null);

                if (victim != null) {
                    Tier victimNewTier = tierAt(currentRank - 1);
                    changes.add(TierChangeDto.builder()
                            .pokemonId(victim.getPokemonId())
                            .pokemonName(victim.getPokemonName())
                            .oldTier(victim.getTier())
                            .newTier(victimNewTier)
                            .build());
                    victim.setTier(victimNewTier);
                }
            }
        }

        // The target itself is the last change
        changes.add(TierChangeDto.builder()
                .pokemonId(target.getPokemonId())
                .pokemonName(target.getPokemonName())
                .oldTier(target.getTier())
                .newTier(toTier)
                .build());

        return changes;
    }
}
