package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.StatData;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierAssignmentServiceTest {

    @Mock private ClosedListRepository closedListRepository;

    private TierAssignmentService service;

    private static final String LEAGUE_ID = "league-1";

    @BeforeEach
    void setUp() {
        service = new TierAssignmentService(closedListRepository);
    }

    @Test
    void assignTiersToPool_emptyPool_doesNothing() {
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of());

        service.assignTiersToPool(LEAGUE_ID, LeagueSettings.defaults());

        verify(closedListRepository, never()).updateTier(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assignTiersToPool_defaultSettings_quintiles() {
        // 5 Pokémon with equal 20% each tier → one per tier
        List<ClosedListEntity> pool = List.of(
                entityWithBst("id1", 620),
                entityWithBst("id2", 560),
                entityWithBst("id3", 500),
                entityWithBst("id4", 440),
                entityWithBst("id5", 380)
        );
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(pool);

        service.assignTiersToPool(LEAGUE_ID, LeagueSettings.defaults());

        verify(closedListRepository).updateTier("id1", Tier.S);
        verify(closedListRepository).updateTier("id2", Tier.A);
        verify(closedListRepository).updateTier("id3", Tier.B);
        verify(closedListRepository).updateTier("id4", Tier.C);
        verify(closedListRepository).updateTier("id5", Tier.D);
    }

    @Test
    void assignTiersToPool_customPercentages() {
        // 10 Pokémon; S=40%, A=30%, B=20%, C=10%, D=0%
        // positions (i*100/10): 0,10,20,30,40,50,60,70,80,90
        // cum = [40,70,90,100]
        // i=0 pct=0  → S, i=1 pct=10 → S, i=2 pct=20 → S, i=3 pct=30 → S   (4 × S)
        // i=4 pct=40 → A, i=5 pct=50 → A, i=6 pct=60 → A                    (3 × A)
        // i=7 pct=70 → B, i=8 pct=80 → B                                     (2 × B)
        // i=9 pct=90 → C                                                      (1 × C)
        LeagueSettings settings = LeagueSettings.builder()
                .tierPctS(40).tierPctA(30).tierPctB(20).tierPctC(10).tierPctD(0).build();

        List<ClosedListEntity> pool = List.of(
                entityWithBst("id01", 700), entityWithBst("id02", 680), entityWithBst("id03", 660),
                entityWithBst("id04", 640), entityWithBst("id05", 620), entityWithBst("id06", 600),
                entityWithBst("id07", 580), entityWithBst("id08", 560), entityWithBst("id09", 540),
                entityWithBst("id10", 520)
        );
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(pool);

        service.assignTiersToPool(LEAGUE_ID, settings);

        verify(closedListRepository).updateTier("id01", Tier.S);
        verify(closedListRepository).updateTier("id02", Tier.S);
        verify(closedListRepository).updateTier("id03", Tier.S);
        verify(closedListRepository).updateTier("id04", Tier.S);
        verify(closedListRepository).updateTier("id05", Tier.A);
        verify(closedListRepository).updateTier("id06", Tier.A);
        verify(closedListRepository).updateTier("id07", Tier.A);
        verify(closedListRepository).updateTier("id08", Tier.B);
        verify(closedListRepository).updateTier("id09", Tier.B);
        verify(closedListRepository).updateTier("id10", Tier.C);
    }

    @Test
    void assignTiersToPool_nullTierPct_fallsBackToDefault20() {
        // Null tierPct values should default to 20% each (same as defaults)
        LeagueSettings settings = LeagueSettings.builder()
                .tierPctS(null).tierPctA(null).tierPctB(null).tierPctC(null).tierPctD(null)
                .build();

        List<ClosedListEntity> pool = List.of(
                entityWithBst("id1", 620),
                entityWithBst("id2", 560),
                entityWithBst("id3", 500),
                entityWithBst("id4", 440),
                entityWithBst("id5", 380)
        );
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(pool);

        service.assignTiersToPool(LEAGUE_ID, settings);

        verify(closedListRepository).updateTier("id1", Tier.S);
        verify(closedListRepository).updateTier("id2", Tier.A);
        verify(closedListRepository).updateTier("id3", Tier.B);
        verify(closedListRepository).updateTier("id4", Tier.C);
        verify(closedListRepository).updateTier("id5", Tier.D);
    }

    @Test
    void assignTiersToPool_nullStats_treatedAsBstZero() {
        // A Pokémon with null stats should be sorted last (BST = 0) and land in D
        ClosedListEntity noStats = new ClosedListEntity();
        noStats.setId("noStats");
        noStats.setStats(null);

        List<ClosedListEntity> pool = List.of(
                entityWithBst("high", 600),
                noStats
        );
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(pool);

        service.assignTiersToPool(LEAGUE_ID, LeagueSettings.defaults());

        // 2 Pokémon with default 20% each tier; cum=[20,40,60,80]
        // i=0 pct=0  → S, i=1 pct=50 → B (50<60)
        verify(closedListRepository).updateTier("high", Tier.S);
        verify(closedListRepository).updateTier("noStats", Tier.B);
    }

    private static ClosedListEntity entityWithBst(String id, int bst) {
        ClosedListEntity e = new ClosedListEntity();
        e.setId(id);
        e.setStats(List.of(new Stat(bst, new StatData("total", ""))));
        return e;
    }
}
