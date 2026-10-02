package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StealClauseServiceTest {

    @Mock private ClosedListRepository closedListRepository;

    private StealClauseService service;

    private static final String LEAGUE_ID = "league-1";
    private static final String POKEMON   = "charizard";

    @BeforeEach
    void setUp() {
        service = new StealClauseService(closedListRepository, new TierPricingService());
    }

    @Test
    void currentClause_customPrice_returnsItWithoutLookingUpTier() {
        assertThat(service.currentClause(league(), pick(800))).isEqualTo(800);
        verifyNoInteractions(closedListRepository);
    }

    @Test
    void currentClause_noCustomPrice_returnsTierPrice() {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setTier(Tier.S);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThat(service.currentClause(league(), pick(null))).isEqualTo(300);
    }

    @Test
    void currentClause_notInClosedList_returnsZero() {
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.empty());

        assertThat(service.currentClause(league(), pick(null))).isZero();
    }

    @Test
    void raiseCost_evenIncrease_costsHalf() {
        assertThat(service.raiseCost(300, 500)).isEqualTo(100);
    }

    @Test
    void raiseCost_oddIncrease_roundsUp() {
        assertThat(service.raiseCost(300, 501)).isEqualTo(101);
    }

    @Test
    void raisedClause_addsTwicePaidCoins() {
        assertThat(service.raisedClause(300, 101)).isEqualTo(502);
    }

    private LeagueEntity league() {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setSettings(LeagueSettings.builder().priceTierS(300).build());
        return league;
    }

    private DraftPick pick(Integer customStealPrice) {
        return new DraftPick("brock", POKEMON, 6, 1, Instant.now(), customStealPrice, null);
    }
}
