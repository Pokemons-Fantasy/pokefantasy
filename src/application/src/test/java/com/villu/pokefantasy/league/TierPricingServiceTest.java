package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TierPricingServiceTest {

    private final TierPricingService service = new TierPricingService();

    @Test
    void priceForTier_nullSettings_returnsZero() {
        assertThat(service.priceForTier(null, Tier.S)).isZero();
    }

    @Test
    void priceForTier_nullTier_returnsZero() {
        LeagueSettings settings = LeagueSettings.builder().priceTierS(500).build();
        assertThat(service.priceForTier(settings, null)).isZero();
    }

    @Test
    void priceForTier_nullPriceField_returnsZero() {
        LeagueSettings settings = LeagueSettings.builder().build();
        assertThat(service.priceForTier(settings, Tier.A)).isZero();
    }

    @Test
    void priceForTier_returnsConfiguredPricePerTier() {
        LeagueSettings settings = LeagueSettings.builder()
                .priceTierS(500).priceTierA(300).priceTierB(200).priceTierC(100).priceTierD(50)
                .build();

        assertThat(service.priceForTier(settings, Tier.S)).isEqualTo(500);
        assertThat(service.priceForTier(settings, Tier.A)).isEqualTo(300);
        assertThat(service.priceForTier(settings, Tier.B)).isEqualTo(200);
        assertThat(service.priceForTier(settings, Tier.C)).isEqualTo(100);
        assertThat(service.priceForTier(settings, Tier.D)).isEqualTo(50);
    }
}
