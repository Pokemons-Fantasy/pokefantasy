package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import org.springframework.stereotype.Service;

@Service
public class TierPricingService {

    public int priceForTier(LeagueSettings settings, Tier tier) {
        if (settings == null || tier == null) return 0;
        Integer price = switch (tier) {
            case S -> settings.getPriceTierS();
            case A -> settings.getPriceTierA();
            case B -> settings.getPriceTierB();
            case C -> settings.getPriceTierC();
            case D -> settings.getPriceTierD();
        };
        return price != null ? price : 0;
    }
}
