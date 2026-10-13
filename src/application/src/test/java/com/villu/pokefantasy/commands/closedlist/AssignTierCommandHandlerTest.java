package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
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
        ClosedListEntity other = entry("e2", "pidgey", Tier.D);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(other));

        assertThatThrownBy(() -> handler.handle(command(Tier.A, "e1", "e2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está en el pool de esta liga");
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    @Test
    void handle_targetsInDifferentTiers_throwsIllegalArgument() {
        ClosedListEntity b = entry("e1", "machamp", Tier.B);
        ClosedListEntity c = entry("e2", "raticate", Tier.C);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(b, c));

        assertThatThrownBy(() -> handler.handle(command(Tier.A, "e1", "e2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mismo tier");
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    // ── same tier → no changes ────────────────────────────────────────────────

    @Test
    void handle_sameTier_returnsEmptyChanges() {
        ClosedListEntity target = entry("e1", "charizard", Tier.S);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target));

        TierAdjustmentResponse result = handler.handle(command(Tier.S, "e1"));

        assertThat(result.getChanges()).isEmpty();
        verify(closedListRepository, never()).updateTier(any(), any());
        verifyNoInteractions(activityEventRepository);
    }

    // ── sin cascada: solo se mueven los elegidos ─────────────────────────────

    @Test
    void handle_promotion_movesOnlyTargets() {
        ClosedListEntity t1 = entry("t1", "machamp", Tier.B);
        ClosedListEntity t2 = entry("t2", "gengar", Tier.B);
        ClosedListEntity a1 = entry("a1", "alakazam", Tier.A);
        ClosedListEntity b1 = entry("b1", "arcanine", Tier.B);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(t1, t2, a1, b1));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "t1", "t2"));

        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getOldTier, TierChangeDto::getNewTier)
                .containsExactly(tuple("machamp", Tier.B, Tier.A), tuple("gengar", Tier.B, Tier.A));
        verify(closedListRepository).updateTier("t1", Tier.A);
        verify(closedListRepository).updateTier("t2", Tier.A);
        verify(closedListRepository, times(2)).updateTier(any(), any());
        verify(leagueAdminGuard).requireLeagueAdmin(LEAGUE, ADMIN);
    }

    @Test
    void handle_multiTierDemotion_movesOnlyTarget() {
        ClosedListEntity target = entry("e-s", "mewtwo", Tier.S);
        ClosedListEntity a = entry("e-a", "dragonite", Tier.A);
        ClosedListEntity c = entry("e-c", "raticate", Tier.C);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target, a, c));

        TierAdjustmentResponse result = handler.handle(command(Tier.D, "e-s"));

        assertThat(result.getChanges())
                .extracting(TierChangeDto::getPokemonName, TierChangeDto::getOldTier, TierChangeDto::getNewTier)
                .containsExactly(tuple("mewtwo", Tier.S, Tier.D));
        verify(closedListRepository).updateTier("e-s", Tier.D);
        verify(closedListRepository, times(1)).updateTier(any(), any());
    }

    @Test
    void handle_duplicatedIds_movesOnce() {
        ClosedListEntity target = entry("e1", "machamp", Tier.B);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(target));

        TierAdjustmentResponse result = handler.handle(command(Tier.A, "e1", "e1"));

        assertThat(result.getChanges()).hasSize(1);
        verify(closedListRepository, times(1)).updateTier("e1", Tier.A);
        verify(activityEventRepository, times(1)).save(any());
    }

    // ── activity ──────────────────────────────────────────────────────────────

    @Test
    void handle_savesOneActivityEventPerTarget() {
        ClosedListEntity t1 = entry("t1", "machamp", Tier.B);
        ClosedListEntity t2 = entry("t2", "gengar", Tier.B);
        when(closedListRepository.findAllByLeagueId(LEAGUE)).thenReturn(List.of(t1, t2));

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
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(AssignTierCommand.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AssignTierCommand command(Tier tier, String... entryIds) {
        return new AssignTierCommand(List.of(entryIds), tier, LEAGUE, ADMIN);
    }

    private ClosedListEntity entry(String id, String name, Tier tier) {
        ClosedListEntity e = new ClosedListEntity();
        e.setId(id);
        e.setPokemonName(name);
        e.setPokemonId(Math.abs(id.hashCode() % 1000) + 1);
        e.setTier(tier);
        e.setLeagueId(LEAGUE);
        return e;
    }
}
