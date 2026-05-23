package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.response.TierAdjustmentResponse;
import com.villu.pokefantasy.response.TierChangeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignTierCommandHandlerTest {

    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private ActivityEventRepository activityEventRepository;

    private AssignTierCommandHandler handler;

    private static final String LEAGUE = "l1";
    private static final String ADMIN  = "ash";

    @BeforeEach
    void setUp() {
        handler = new AssignTierCommandHandler(closedListRepository, leagueAdminGuard, activityEventRepository);
    }

    // ── existing validation tests ─────────────────────────────────────────────

    @Test
    void handle_nullEntryId_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AssignTierCommand(null, Tier.S, LEAGUE, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryId and tier are required");
    }

    @Test
    void handle_nullTier_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AssignTierCommand("e1", null, LEAGUE, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryId and tier are required");
    }

    @Test
    void handle_entryNotFound_throwsIllegalArgument() {
        when(closedListRepository.findById("e1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new AssignTierCommand("e1", Tier.A, LEAGUE, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void handle_entryBelongsToDifferentLeague_throwsIllegalArgument() {
        ClosedListEntity entry = entry("e1", "poke", Tier.B, 300, LEAGUE + "-other");
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new AssignTierCommand("e1", Tier.B, LEAGUE, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    // ── same tier → no changes ────────────────────────────────────────────────

    @Test
    void handle_sameTier_returnsEmptyChanges() {
        ClosedListEntity target = entry("e1", "charizard", Tier.S, 600, LEAGUE);
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(target));

        TierAdjustmentResponse result = handler.handle(new AssignTierCommand("e1", Tier.S, LEAGUE, ADMIN));

        assertThat(result.getChanges()).isEmpty();
        verify(closedListRepository, never()).findAllByLeagueId(any());
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    // ── adjacent promotion B→A: 1 victim ─────────────────────────────────────

    @Test
    void handle_adjacentPromotion_singleSwap() {
        // Target: B tier. One pokemon in A tier (victim). After: target→A, victim→B.
        ClosedListEntity target = entry("e-target", "machamp", Tier.B, 505, LEAGUE);
        ClosedListEntity victimA = entry("e-victim", "alakazam", Tier.A, 490, LEAGUE);

        when(closedListRepository.findById("e-target")).thenReturn(Optional.of(target));
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, victimA));

        TierAdjustmentResponse result = handler.handle(new AssignTierCommand("e-target", Tier.A, LEAGUE, ADMIN));

        assertThat(result.getChanges()).hasSize(2);
        // victim bumped down
        assertThat(result.getChanges()).anyMatch(c -> c.getPokemonName().equals("alakazam")
                && c.getOldTier() == Tier.A && c.getNewTier() == Tier.B);
        // target promoted
        assertThat(result.getChanges()).anyMatch(c -> c.getPokemonName().equals("machamp")
                && c.getOldTier() == Tier.B && c.getNewTier() == Tier.A);

        // 2 updateTier calls
        verify(closedListRepository, times(2)).updateTier(any(), any());
    }

    // ── adjacent demotion A→B: 1 victim ──────────────────────────────────────

    @Test
    void handle_adjacentDemotion_singleSwap() {
        // Target: A tier. One pokemon in B tier (victim). After: target→B, victim→A.
        ClosedListEntity target = entry("e-target", "alakazam", Tier.A, 490, LEAGUE);
        ClosedListEntity victimB = entry("e-victim", "machamp", Tier.B, 505, LEAGUE);

        when(closedListRepository.findById("e-target")).thenReturn(Optional.of(target));
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, victimB));

        TierAdjustmentResponse result = handler.handle(new AssignTierCommand("e-target", Tier.B, LEAGUE, ADMIN));

        assertThat(result.getChanges()).hasSize(2);
        assertThat(result.getChanges()).anyMatch(c -> c.getPokemonName().equals("machamp")
                && c.getOldTier() == Tier.B && c.getNewTier() == Tier.A);
        assertThat(result.getChanges()).anyMatch(c -> c.getPokemonName().equals("alakazam")
                && c.getOldTier() == Tier.A && c.getNewTier() == Tier.B);
    }

    // ── multi-tier promotion D→S: 4 victims, 5 updateTier calls ──────────────

    @Test
    void handle_multiTierPromotion_cascade() {
        // D tier target jumps to S. Need one victim from each of S, A, B, C.
        ClosedListEntity target   = entry("e-d",  "magikarp",  Tier.D, 200, LEAGUE);
        ClosedListEntity victimS  = entry("e-s",  "mewtwo",    Tier.S, 680, LEAGUE);
        ClosedListEntity victimA  = entry("e-a",  "dragonite", Tier.A, 600, LEAGUE);
        ClosedListEntity victimB  = entry("e-b",  "machamp",   Tier.B, 505, LEAGUE);
        ClosedListEntity victimC  = entry("e-c",  "raticate",  Tier.C, 413, LEAGUE);

        when(closedListRepository.findById("e-d")).thenReturn(Optional.of(target));
        when(closedListRepository.findAllByLeagueId(LEAGUE))
                .thenReturn(List.of(target, victimS, victimA, victimB, victimC));

        TierAdjustmentResponse result = handler.handle(new AssignTierCommand("e-d", Tier.S, LEAGUE, ADMIN));

        // 4 victims + 1 target = 5 changes
        assertThat(result.getChanges()).hasSize(5);
        // target ends at S
        assertThat(result.getChanges()).anyMatch(c -> c.getPokemonName().equals("magikarp")
                && c.getNewTier() == Tier.S);
        // 5 updateTier calls total
        verify(closedListRepository, times(5)).updateTier(any(), any());
    }

    // ── empty destination tier: promotes without cascade ─────────────────────

    @Test
    void handle_emptyTargetTier_promotesWithoutVictim() {
        // Target in B. No pokemon in A. Promotion B→A: no victim, only target changes.
        ClosedListEntity target = entry("e-target", "machamp", Tier.B, 505, LEAGUE);
        // Pool has only the target (no A-tier pokemon)
        when(closedListRepository.findById("e-target")).thenReturn(Optional.of(target));
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target));

        TierAdjustmentResponse result = handler.handle(new AssignTierCommand("e-target", Tier.A, LEAGUE, ADMIN));

        assertThat(result.getChanges()).hasSize(1);
        assertThat(result.getChanges().get(0).getPokemonName()).isEqualTo("machamp");
        assertThat(result.getChanges().get(0).getNewTier()).isEqualTo(Tier.A);
        verify(closedListRepository, times(1)).updateTier(any(), any());
    }

    // ── adapted valid command test ────────────────────────────────────────────

    @Test
    void handle_validCommand_updatesTierAndReturnsChanges() {
        ClosedListEntity target = entry("e1", "charizard", Tier.D, 534, LEAGUE);
        ClosedListEntity other  = entry("e2", "blastoise", Tier.C, 530, LEAGUE);

        when(closedListRepository.findById("e1")).thenReturn(Optional.of(target));
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, other));

        TierAdjustmentResponse result = handler.handle(new AssignTierCommand("e1", Tier.C, LEAGUE, ADMIN));

        assertThat(result.getChanges()).isNotEmpty();
        verify(leagueAdminGuard).requireLeagueAdmin(LEAGUE, ADMIN);
    }

    @Test
    void handle_validPromotion_savesActivityEvent() {
        ClosedListEntity target = entry("e-target", "machamp", Tier.B, 505, LEAGUE);
        ClosedListEntity victimA = entry("e-victim", "alakazam", Tier.A, 490, LEAGUE);

        when(closedListRepository.findById("e-target")).thenReturn(Optional.of(target));
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, victimA));

        handler.handle(new AssignTierCommand("e-target", Tier.A, LEAGUE, ADMIN));

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository).save(captor.capture());
        ActivityEventEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(ActivityEventType.TIER_CHANGE);
        assertThat(saved.getLeagueId()).isEqualTo(LEAGUE);
        assertThat(saved.getActorUsername()).isEqualTo(ADMIN);
        assertThat(saved.getPokemonName()).isEqualTo("machamp");
        assertThat(saved.getFromTier()).isEqualTo("B");
        assertThat(saved.getToTier()).isEqualTo("A");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(AssignTierCommand.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ClosedListEntity entry(String id, String name, Tier tier, int bst, String leagueId) {
        ClosedListEntity e = new ClosedListEntity();
        e.setId(id);
        e.setPokemonName(name);
        e.setPokemonId(Math.abs(id.hashCode() % 1000) + 1);
        e.setTier(tier);
        e.setLeagueId(leagueId);
        // single stat with the given bst value
        e.setStats(List.of(new Stat(bst, null)));
        return e;
    }
}
