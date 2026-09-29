package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrepareDraftCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private TierAssignmentService tierAssignmentService;
    @Mock private LeagueRepository leagueRepository;

    private PrepareDraftCommandHandler handler;
    private final LeagueEntity league = new LeagueEntity();

    @BeforeEach
    void setUp() {
        handler = new PrepareDraftCommandHandler(draftRepository, leagueAdminGuard, closedListRepository,
                tierAssignmentService, leagueRepository);
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0))));
    }

    private void admin() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
    }

    private static DraftEntity draftWith(DraftStatus status) {
        DraftEntity draft = new DraftEntity();
        draft.setStatus(status);
        return draft;
    }

    @Test
    void handle_noDraft_createsPendingDraftWithDefaultsAndAssignsTiers() {
        admin();
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(new ClosedListEntity()));

        handler.handle(new PrepareDraftCommand("l1", "ash"));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        DraftEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(DraftStatus.PENDING);
        assertThat(saved.getLeagueId()).isEqualTo("l1");
        assertThat(saved.getTurnOrder()).containsExactly("ash", "misty");
        assertThat(saved.getConfig()).isEqualTo(DraftConfig.defaults());
        assertThat(saved.getPicks()).isEmpty();
        assertThat(saved.getDraftHistory()).isEmpty();
        assertThat(saved.getCurrentRound()).isEqualTo(1);
        verify(tierAssignmentService).assignTiersToPool(eq("l1"), any(LeagueSettings.class));
    }

    // Sin ajustes guardados, GET settings devuelve los de por defecto (20 rondas) y el draft usaría 10:
    // al preparar se guardan para que front y back cuenten las mismas rondas.
    @Test
    void handle_leagueWithoutSettings_savesDefaultSettings() {
        admin();
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(new ClosedListEntity()));

        handler.handle(new PrepareDraftCommand("l1", "ash"));

        assertThat(league.getSettings()).isEqualTo(LeagueSettings.defaults());
        verify(leagueRepository).save(league);
    }

    @Test
    void handle_leagueWithSettings_keepsThem() {
        LeagueSettings settings = LeagueSettings.builder().maxTeamSize(12).build();
        league.setSettings(settings);
        admin();
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(new ClosedListEntity()));

        handler.handle(new PrepareDraftCommand("l1", "ash"));

        assertThat(league.getSettings()).isSameAs(settings);
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void handle_afterCancelledDraft_allowed() {
        admin();
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draftWith(DraftStatus.CANCELLED)));
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(new ClosedListEntity()));

        handler.handle(new PrepareDraftCommand("l1", "ash"));

        verify(draftRepository).save(any(DraftEntity.class));
    }

    @ParameterizedTest
    @EnumSource(value = DraftStatus.class, names = {"PENDING", "IN_PROGRESS", "COMPLETED"})
    void handle_latestDraftNotCancelled_throws(DraftStatus status) {
        admin();
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draftWith(status)));

        assertThatThrownBy(() -> handler.handle(new PrepareDraftCommand("l1", "ash")))
                .isInstanceOf(IllegalStateException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void handle_emptyPool_throws() {
        admin();
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of());

        assertThatThrownBy(() -> handler.handle(new PrepareDraftCommand("l1", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El pool está vacío: nominad Pokémon antes de preparar el draft");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(PrepareDraftCommand.class);
    }
}
