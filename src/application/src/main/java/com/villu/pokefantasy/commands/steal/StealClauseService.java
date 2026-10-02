package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

/**
 * Cláusula de robo de un Pokémon: lo que paga el ladrón y cobra el dueño.
 * Vale el precio de su tier hasta que el dueño la sube; cada moneda invertida suma {@link #RAISE_MULTIPLIER}.
 */
@Service
public class StealClauseService {

    public static final int RAISE_MULTIPLIER = 2;

    private final ClosedListRepository closedListRepository;
    private final TierPricingService tierPricingService;

    public StealClauseService(ClosedListRepository closedListRepository, TierPricingService tierPricingService) {
        this.closedListRepository = closedListRepository;
        this.tierPricingService = tierPricingService;
    }

    public int currentClause(LeagueEntity league, DraftPick pick) {
        if (pick.getCustomStealPrice() != null) {
            return pick.getCustomStealPrice();
        }
        var tier = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pick.getPokemonName(), league.getId())
                .map(ClosedListEntity::getTier)
                .orElse(null);
        return tierPricingService.priceForTier(league.getSettings(), tier);
    }

    /** Monedas para llevar la cláusula de {@code current} a {@code requested} o más (se redondea hacia arriba). */
    public int raiseCost(int current, int requested) {
        return Math.ceilDiv(requested - current, RAISE_MULTIPLIER);
    }

    public int raisedClause(int current, int paidCoins) {
        return current + paidCoins * RAISE_MULTIPLIER;
    }
}
