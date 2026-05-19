package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignTierCommandHandlerTest {

    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;

    private AssignTierCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AssignTierCommandHandler(closedListRepository, leagueAdminGuard);
    }

    @Test
    void handle_nullEntryId_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AssignTierCommand(null, Tier.S, "l1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryId and tier are required");
    }

    @Test
    void handle_nullTier_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new AssignTierCommand("e1", null, "l1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryId and tier are required");
    }

    @Test
    void handle_entryNotFound_throwsIllegalArgument() {
        when(closedListRepository.findById("e1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new AssignTierCommand("e1", Tier.A, "l1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void handle_entryBelongsToDifferentLeague_throwsIllegalArgument() {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setLeagueId("other-league");
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new AssignTierCommand("e1", Tier.B, "l1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void handle_validCommand_updatesTier() {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setLeagueId("l1");
        when(closedListRepository.findById("e1")).thenReturn(Optional.of(entry));

        handler.handle(new AssignTierCommand("e1", Tier.S, "l1", "ash"));

        verify(closedListRepository).updateTier("e1", Tier.S);
        verify(leagueAdminGuard).requireLeagueAdmin("l1", "ash");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(AssignTierCommand.class);
    }
}
