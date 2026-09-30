package com.villu.pokefantasy.commands.closedlist;

import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
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
class DenominatePokemonCommandHandlerTest {

    @Mock private ClosedListRepository closedListRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private DenominatePokemonCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DenominatePokemonCommandHandler(closedListRepository, draftRepository, leagueMembershipGuard);
    }

    @Test
    void handle_notLeagueMember_propagatesForbidden() {
        when(leagueMembershipGuard.requireMember("l1", "ash"))
                .thenThrow(new ForbiddenOperationException("not a member"));

        assertThatThrownBy(() -> handler.handle(new DenominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_draftNotPending_throwsIllegalState() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new DenominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ya no se pueden quitar nominaciones");
    }

    @Test
    void handle_noDraft_deletesEntry() {
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        handler.handle(new DenominatePokemonCommand("ash", "pikachu", "l1"));

        verify(closedListRepository).deleteByPokemonNameAndNominatedByAndLeagueId("pikachu", "ash", "l1");
    }

    @Test
    void handle_draftInSetup_throwsIllegalState() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new DenominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ya no se pueden quitar nominaciones: se está preparando el draft");
        verify(closedListRepository, never()).deleteByPokemonNameAndNominatedByAndLeagueId(any(), any(), any());
    }

    @Test
    void handle_lastDraftCancelled_deletesEntry() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.CANCELLED);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new DenominatePokemonCommand("ash", "pikachu", "l1"));

        verify(closedListRepository).deleteByPokemonNameAndNominatedByAndLeagueId("pikachu", "ash", "l1");
    }

    @Test
    void handle_seasonStarted_throwsWithSeasonMessage() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new DenominatePokemonCommand("ash", "pikachu", "l1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ya no se pueden quitar nominaciones: la temporada ya ha empezado");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(DenominatePokemonCommand.class);
    }
}
