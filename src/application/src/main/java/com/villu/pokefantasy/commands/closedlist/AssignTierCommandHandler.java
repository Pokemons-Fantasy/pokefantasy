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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        // (sin cascada) y no se toca con el draft en curso.
        draftRepository.findLatestByLeagueId(command.leagueId())
                .filter(d -> d.getStatus() == DraftStatus.PENDING || d.getStatus() == DraftStatus.IN_PROGRESS)
                .ifPresent(d -> {
                    throw new IllegalStateException("Los tiers solo se ajustan aquí con el draft terminado");
                });

        List<ClosedListEntity> pool = closedListRepository.findAllByLeagueId(command.leagueId());
        Set<String> targetIds = new HashSet<>(command.entryIds());
        List<ClosedListEntity> targets = pool.stream().filter(e -> targetIds.contains(e.getId())).toList();

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

        Map<String, TierChangeDto> changes = computeCascade(targets, toTier, pool);
        changes.forEach((entryId, change) -> closedListRepository.updateTier(entryId, change.getNewTier()));

        // Un evento por cada Pokémon movido a mano; los de la cascada no se registran
        Instant now = Instant.now();
        for (ClosedListEntity target : targets) {
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

        return TierAdjustmentResponse.builder().changes(new ArrayList<>(changes.values())).build();
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
     * Computes the tier changes needed to move the N {@code targets} (all in the same tier) to {@code toTier},
     * cascading N victims one step per tier in between to keep tier counts balanced.
     *
     * Promotion (toRank < fromRank): for each tier T from toRank to fromRank-1,
     *   the N lowest-BST pokemon in T (not targets) go down one tier.
     *
     * Demotion (toRank > fromRank): for each tier T from toRank down to fromRank+1,
     *   the N highest-BST pokemon in T (not targets) go up one tier.
     *
     * BST ties are broken by name. A victim pushed into the next tier can be picked again there; it then
     * appears once, with its original and final tier. Keyed by entry id, in the order they first moved.
     */
    private Map<String, TierChangeDto> computeCascade(List<ClosedListEntity> targets, Tier toTier,
                                                      List<ClosedListEntity> pool) {
        Set<String> targetIds = new HashSet<>(targets.stream().map(ClosedListEntity::getId).toList());
        int fromRank = rank(targets.get(0).getTier());
        int toRank   = rank(toTier);
        boolean promotion = toRank < fromRank;
        int step = promotion ? 1 : -1;

        Comparator<ClosedListEntity> byBst = Comparator.comparingInt(this::bst);
        Comparator<ClosedListEntity> victimOrder = (promotion ? byBst : byBst.reversed())
                .thenComparing(ClosedListEntity::getPokemonName);

        Map<String, TierChangeDto> changes = new LinkedHashMap<>();

        for (int r = toRank; r != fromRank; r += step) {
            Tier tier = tierAt(r);
            Tier victimNewTier = tierAt(r + step);
            List<ClosedListEntity> victims = pool.stream()
                    .filter(e -> e.getTier() == tier)
                    .filter(e -> !targetIds.contains(e.getId()))
                    .sorted(victimOrder)
                    .limit(targets.size())
                    .toList();
            // Update in-memory so subsequent iterations see the new state
            victims.forEach(victim -> move(victim, victimNewTier, changes));
        }

        targets.forEach(target -> move(target, toTier, changes));
        return changes;
    }

    private void move(ClosedListEntity entry, Tier newTier, Map<String, TierChangeDto> changes) {
        TierChangeDto previous = changes.get(entry.getId());
        changes.put(entry.getId(), TierChangeDto.builder()
                .pokemonId(entry.getPokemonId())
                .pokemonName(entry.getPokemonName())
                .oldTier(previous == null ? entry.getTier() : previous.getOldTier())
                .newTier(newTier)
                .build());
        entry.setTier(newTier);
    }
}
