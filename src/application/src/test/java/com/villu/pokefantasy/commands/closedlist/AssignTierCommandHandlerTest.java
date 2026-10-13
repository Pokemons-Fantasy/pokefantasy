package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.response.TierAdjustmentResponse;
import com.villu.pokefantasy.response.TierChangeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignTierCommandHandlerTest {

    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private DraftRepository draftRepository;

    private AssignTierCommandHandler handler;

    private static final String LEAGUE = "l1";
    private static final String ADMIN  = "ash";

    @BeforeEach
    void setUp() {
        handler = new AssignTierCommandHandler(closedListRepository, leagueAdminGuard, activityEventRepository,
                draftRepository);
    }

    // Con el draft en preparación o en curso el tier fija el precio de los picks: no se toca desde aquí.
    @ParameterizedTest
    @EnumSource(value = DraftStatus.class, names = {"PENDING", "IN_PROGRESS"})
    void handle_draftInSetupOrInProgress_throwsAndChangesNothing(DraftStatus status) {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(status);
        when(draftRepository.findLatestByLeagueId(LEAGUE)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(command(Tier.A, "e1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Los tiers solo se ajustan aquí con el draft terminado");
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    void handle_nullEntryIds_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AssignTierCommand(null, Tier.S, LEAGUE, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Indica los Pokémon y el tier");
    }

    @Test
    void handle_emptyEntryIds_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AssignTierCommand(List.of(), Tier.S, LEAGUE, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Indica los Pokémon y el tier");
    }

    @Test
    void handle_nullTier_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(command(null, "e1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Indica los Pokémon y el tier");
    }

    // El pool solo trae los de la liga: un id de otra liga o inexistente no aparece.
    @Test
    void handle_entryNotInLeaguePool_throwsIllegalArgument() {
        ClosedListEntity other = entry("e2", "pidgey", Tier.D, 251);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(other));

        assertThatThrownBy(() -> handler.handle(command(Tier.A, "e1", "e2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está en el pool de esta liga");
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    @Test
    void handle_targetsInDifferentTiers_throwsIllegalArgument() {
        ClosedListEntity b = entry("e1", "machamp", Tier.B, 505);
        ClosedListEntity c = entry("e2", "raticate", Tier.C, 413);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(b, c));

        assertThatThrownBy(() -> handler.handle(command(Tier.A, "e1", "e2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mismo tier");
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    // ── same tier → no changes ────────────────────────────────────────────────

    @Test
    void handle_sameTier_returnsEmptyChanges() {
        ClosedListEntity target = entry("e1", "charizard", Tier.S, 600);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target));

        TierAdjustmentResponse result = handler.handle(command(Tier.S, "e1"));

        assertThat(result.getChanges()).isEmpty();
        verify(closedListRepository, never()).updateTier(any(), any());
        verifyNoInteractions(activityEventRepository);
    }

    // ── one pokemon (same behaviour as before multi-select) ───────────────────

    @Test
    void handle_adjacentPromotion_singleSwap() {
        // Target: B tier. One pokemon in A tier (victim). After: target→A, victim→B.
        ClosedListEntity target = entry("e-target", "machamp", Tier.B, 505);
        ClosedListEntity victimA = entry("e-victim", "alakazam", Tier.A, 490);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, victimA));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "e-target"));

        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getOldTier, TierChangeDto::getNewTier)
                .containsExactly(tuple("alakazam", Tier.A, Tier.B), tuple("machamp", Tier.B, Tier.A));
        verify(closedListRepository).updateTier("e-victim", Tier.B);
        verify(closedListRepository).updateTier("e-target", Tier.A);
    }

    @Test
    void handle_adjacentDemotion_singleSwap() {
        // Target: A tier. One pokemon in B tier (victim). After: target→B, victim→A.
        ClosedListEntity target = entry("e-target", "alakazam", Tier.A, 490);
        ClosedListEntity victimB = entry("e-victim", "machamp", Tier.B, 505);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, victimB));

        TierAdjustmentResponse result = handler.handle(command(Tier.B, "e-target"));

        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getOldTier, TierChangeDto::getNewTier)
                .containsExactly(tuple("machamp", Tier.B, Tier.A), tuple("alakazam", Tier.A, Tier.B));
    }

    @Test
    void handle_multiTierPromotion_cascade() {
        // D tier target jumps to S. Need one victim from each of S, A, B, C.
        ClosedListEntity target   = entry("e-d",  "magikarp",  Tier.D, 200);
        ClosedListEntity victimS  = entry("e-s",  "mewtwo",    Tier.S, 680);
        ClosedListEntity victimA  = entry("e-a",  "dragonite", Tier.A, 600);
        ClosedListEntity victimB  = entry("e-b",  "machamp",   Tier.B, 505);
        ClosedListEntity victimC  = entry("e-c",  "raticate",  Tier.C, 413);
        when(closedListRepository.findAllByLeagueId(LEAGUE))
                .thenReturn(List.of(target, victimS, victimA, victimB, victimC));

        TierAdjustmentResponse result = handler.handle(command(Tier.S, "e-d"));

        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getOldTier, TierChangeDto::getNewTier)
                .containsExactly(
                        tuple("mewtwo", Tier.S, Tier.A),
                        tuple("dragonite", Tier.A, Tier.B),
                        tuple("machamp", Tier.B, Tier.C),
                        tuple("raticate", Tier.C, Tier.D),
                        tuple("magikarp", Tier.D, Tier.S));
        verify(closedListRepository, times(5)).updateTier(any(), any());
    }

    @Test
    void handle_emptyTargetTier_promotesWithoutVictim() {
        // Target in B. No pokemon in A. Promotion B→A: no victim, only target changes.
        ClosedListEntity target = entry("e-target", "machamp", Tier.B, 505);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "e-target"));

        assertThat(result.getChanges()).hasSize(1);
        assertThat(result.getChanges().get(0).getPokemonName()).isEqualTo("machamp");
        assertThat(result.getChanges().get(0).getNewTier()).isEqualTo(Tier.A);
        verify(closedListRepository, times(1)).updateTier(any(), any());
        verify(leagueAdminGuard).requireLeagueAdmin(LEAGUE, ADMIN);
    }

    // ── several pokemon at once ───────────────────────────────────────────────

    @Test
    void handle_twoTargetsAdjacentPromotion_twoWeakestVictimsGoDown() {
        ClosedListEntity t1 = entry("t1", "machamp", Tier.B, 505);
        ClosedListEntity t2 = entry("t2", "gengar", Tier.B, 500);
        ClosedListEntity a1 = entry("a1", "dragonite", Tier.A, 600);
        ClosedListEntity a2 = entry("a2", "alakazam", Tier.A, 490);
        ClosedListEntity a3 = entry("a3", "arcanine", Tier.A, 555);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(t1, t2, a1, a2, a3));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "t1", "t2"));

        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getOldTier, TierChangeDto::getNewTier)
                .containsExactly(
                        tuple("alakazam", Tier.A, Tier.B),
                        tuple("arcanine", Tier.A, Tier.B),
                        tuple("machamp", Tier.B, Tier.A),
                        tuple("gengar", Tier.B, Tier.A));
        verify(closedListRepository, never()).updateTier(eq("a1"), any());
    }

    // Empate de BST: al bajar suben los de más BST y, a igualdad, el primero por nombre (como con uno solo).
    @Test
    void handle_twoTargetsDemotion_strongestVictimsGoUpAndTiesByName() {
        ClosedListEntity t1 = entry("t1", "alakazam", Tier.A, 490);
        ClosedListEntity t2 = entry("t2", "arcanine", Tier.A, 555);
        ClosedListEntity b1 = entry("b1", "zangoose", Tier.B, 458);
        ClosedListEntity b2 = entry("b2", "absol", Tier.B, 465);
        ClosedListEntity b3 = entry("b3", "ninetales", Tier.B, 505);
        ClosedListEntity b4 = entry("b4", "machamp", Tier.B, 505);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(t1, t2, b1, b2, b3, b4));

        TierAdjustmentResponse result = handler.handle(command(Tier.B, "t1", "t2"));

        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getNewTier)
                .containsExactly(
                        tuple("machamp", Tier.A),
                        tuple("ninetales", Tier.A),
                        tuple("alakazam", Tier.B),
                        tuple("arcanine", Tier.B));
    }

    // Un Pokémon que cae al tier siguiente puede volver a caer: sale una vez, con su tier de origen y el final.
    @Test
    void handle_twoTargetsMultiTier_victimPickedTwiceAppearsOnce() {
        ClosedListEntity t1 = entry("t1", "magikarp", Tier.C, 200);
        ClosedListEntity t2 = entry("t2", "caterpie", Tier.C, 195);
        ClosedListEntity a1 = entry("a1", "dragonite", Tier.A, 500);
        ClosedListEntity a2 = entry("a2", "alakazam", Tier.A, 490);
        ClosedListEntity b1 = entry("b1", "machamp", Tier.B, 495);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(t1, t2, a1, a2, b1));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "t1", "t2"));

        // A: alakazam y dragonite bajan a B; B: alakazam (490) y machamp (495) bajan a C.
        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getOldTier, TierChangeDto::getNewTier)
                .containsExactly(
                        tuple("alakazam", Tier.A, Tier.C),
                        tuple("dragonite", Tier.A, Tier.B),
                        tuple("machamp", Tier.B, Tier.C),
                        tuple("magikarp", Tier.C, Tier.A),
                        tuple("caterpie", Tier.C, Tier.A));
        verify(closedListRepository).updateTier("a2", Tier.C);
        verify(closedListRepository, times(5)).updateTier(any(), any());
    }

    @Test
    void handle_duplicatedIds_movesOnce() {
        ClosedListEntity target = entry("e1", "machamp", Tier.B, 505);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "e1", "e1"));

        assertThat(result.getChanges()).hasSize(1);
        verify(activityEventRepository, times(1)).save(any());
    }

    // ── activity ──────────────────────────────────────────────────────────────

    @Test
    void handle_savesOneActivityEventPerTargetAndNoneForVictims() {
        ClosedListEntity t1 = entry("t1", "machamp", Tier.B, 505);
        ClosedListEntity t2 = entry("t2", "gengar", Tier.B, 500);
        ClosedListEntity a1 = entry("a1", "alakazam", Tier.A, 490);
        ClosedListEntity a2 = entry("a2", "arcanine", Tier.A, 555);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(t1, t2, a1, a2));

        handler.handle(command(Tier.A, "t1", "t2"));

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ActivityEventEntity::getPokemonName, ActivityEventEntity::getFromTier, ActivityEventEntity::getToTier)
                .containsExactly(tuple("machamp", "B", "A"), tuple("gengar", "B", "A"));
        ActivityEventEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(ActivityEventType.TIER_CHANGE);
        assertThat(saved.getLeagueId()).isEqualTo(LEAGUE);
        assertThat(saved.getActorUsername()).isEqualTo(ADMIN);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void handle_entryWithoutStats_countsAsZeroBst() {
        ClosedListEntity target = entry("t1", "machamp", Tier.B, 505);
        ClosedListEntity noStats = entry("a1", "missingno", Tier.A, 0);
        noStats.setStats(null);
        ClosedListEntity a2 = entry("a2", "alakazam", Tier.A, 490);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, noStats, a2));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "t1"));

        assertThat(result.getChanges().get(0).getPokemonName()).isEqualTo("missingno");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(AssignTierCommand.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AssignTierCommand command(Tier tier, String... entryIds) {
        return new AssignTierCommand(List.of(entryIds), tier, LEAGUE, ADMIN);
    }

    private ClosedListEntity entry(String id, String name, Tier tier, int bst) {
        ClosedListEntity e = new ClosedListEntity();
        e.setId(id);
        e.setPokemonName(name);
        e.setPokemonId(Math.abs(id.hashCode() % 1000) + 1);
        e.setTier(tier);
        e.setLeagueId(LEAGUE);
        // single stat with the given bst value
        e.setStats(List.of(new Stat(bst, null)));
        return e;
    }
}
