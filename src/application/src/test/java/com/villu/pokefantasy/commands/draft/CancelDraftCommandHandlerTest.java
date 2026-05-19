package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
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
class CancelDraftCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;

    private CancelDraftCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CancelDraftCommandHandler(draftRepository, leagueAdminGuard);
    }

    @Test
    void handle_noActiveDraft_throwsIllegalState() {
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new CancelDraftCommand("l1", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active draft");
    }

    @Test
    void handle_validCommand_setsCancelledAndSaves() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new CancelDraftCommand("l1", "ash"));

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.CANCELLED);
        verify(draftRepository).save(draft);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(CancelDraftCommand.class);
    }
}
