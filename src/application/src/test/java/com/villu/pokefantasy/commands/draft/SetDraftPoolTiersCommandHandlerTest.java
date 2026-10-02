package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetDraftPoolTiersCommandHandlerTest {

    @Mock private DraftSetupGuard draftSetupGuard;
    @Mock private ClosedListRepository closedListRepository;

    private SetDraftPoolTiersCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SetDraftPoolTiersCommandHandler(draftSetupGuard, closedListRepository);
    }

    private static ClosedListEntity entry(String id, String leagueId) {
        ClosedListEntity e = new ClosedListEntity();
        e.setId(id);
        e.setLeagueId(leagueId);
        return e;
    }

    @Test
    void handle_movesEveryEntryOnce() {
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(entry("e1", "l1")));
        when(closedListRepository.findById("e2")).thenReturn(Optional.of(entry("e2", "l1")));

        handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of("e1", "e2", "e1"), Tier.A));

        verify(draftSetupGuard).requireDraftInSetup("l1", "ash");
        verify(closedListRepository).updateTier("e1", Tier.A);
        verify(closedListRepository).updateTier("e2", Tier.A);
    }

    @Test
    void handle_entryOfAnotherLeague_throwsAndUpdatesNothing() {
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(entry("e1", "l1")));
        when(closedListRepository.findById("e2")).thenReturn(Optional.of(entry("e2", "otra")));

        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of("e1", "e2"), Tier.A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese Pokémon no está en el pool de esta liga");
        verify(closedListRepository, never()).updateTier(any(), any());
    }

    @Test
    void handle_emptyIdsOrNullTier_throws() {
        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of(), Tier.A)))
                .hasMessage("Elige al menos un Pokémon");
        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", null, Tier.A)))
                .hasMessage("Elige al menos un Pokémon");
        assertThatThrownBy(() -> handler.handle(new SetDraftPoolTiersCommand("l1", "ash", List.of("e1"), null)))
                .hasMessage("Indica el tier");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(SetDraftPoolTiersCommand.class);
    }
}
