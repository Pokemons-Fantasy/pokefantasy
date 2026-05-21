package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TierAssignmentService {

    private final ClosedListRepository closedListRepository;

    public TierAssignmentService(ClosedListRepository closedListRepository) {
        this.closedListRepository = closedListRepository;
    }

    /**
     * Assigns tiers to all Pokémon in the pool using a BST-based cumulative threshold algorithm.
     * The highest-BST Pokémon fill S first, then A, B, C, D — proportional to the configured percentages.
     * No-ops if the pool is empty.
     */
    public void assignTiersToPool(String leagueId, LeagueSettings settings) {
        List<ClosedListEntity> pool = closedListRepository.findAllByLeagueId(leagueId);
        if (pool.isEmpty()) return;

        int pS = settings.getTierPctS() != null ? settings.getTierPctS() : 20;
        int pA = settings.getTierPctA() != null ? settings.getTierPctA() : 20;
        int pB = settings.getTierPctB() != null ? settings.getTierPctB() : 20;
        int pC = settings.getTierPctC() != null ? settings.getTierPctC() : 20;

        // Cumulative thresholds: S=[0,pS), A=[pS,pS+pA), B=[...], C=[...], D=rest
        int[] cum = { pS, pS + pA, pS + pA + pB, pS + pA + pB + pC };

        List<ClosedListEntity> sorted = pool.stream()
                .sorted(Comparator.comparingInt(this::bst).reversed())
                .collect(Collectors.toList());

        int n = sorted.size();
        List<Tier> tiers = List.of(Tier.S, Tier.A, Tier.B, Tier.C, Tier.D);
        for (int i = 0; i < n; i++) {
            int pct = i * 100 / n;  // position percentage (0–99)
            int tierIndex = 4;      // D by default (absorbs remainder)
            for (int t = 0; t < 4; t++) {
                if (pct < cum[t]) { tierIndex = t; break; }
            }
            closedListRepository.updateTier(sorted.get(i).getId(), tiers.get(tierIndex));
        }
    }

    private int bst(ClosedListEntity entity) {
        if (entity.getStats() == null) return 0;
        return entity.getStats().stream().mapToInt(Stat::getBaseStat).sum();
    }
}
