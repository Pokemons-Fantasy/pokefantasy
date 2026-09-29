package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftSetupGuardTest {

    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private DraftRepository draftRepository;

    @Test
    void requireDraftInSetup_pendingDraft_returnsLeagueAndDraft() {
        LeagueEntity league = new LeagueEntity();
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        DraftSetupGuard.DraftSetup setup = new DraftSetupGuard(leagueAdminGuard, draftRepository)
                .requireDraftInSetup("l1", "ash");

        assertThat(setup.league()).isSameAs(league);
        assertThat(setup.draft()).isSameAs(draft);
    }

    @Test
    void requireDraftInSetup_draftInProgress_throws() {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> new DraftSetupGuard(leagueAdminGuard, draftRepository).requireDraftInSetup("l1", "ash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El draft no se está preparando");
    }
}
