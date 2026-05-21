package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.StatData;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartDraftCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;

    private StartDraftCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String ADMIN = "ash";

    @BeforeEach
    void setUp() {
        handler = new StartDraftCommandHandler(draftRepository, leagueAdminGuard, closedListRepository, leagueRepository);
    }

    private void allowAdmin() {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(List.of(new LeagueMember(ADMIN, LeagueRole.ADMIN, 0)));
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, ADMIN)).thenReturn(league);
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());
    }

    /** Stub leagueRepository for tests that exercise assignTiersToPool with a non-empty pool. */
    private void allowAdminWithDefaultSettings() {
        allowAdmin();
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        // No settings set → handler falls back to LeagueSettings.defaults() (20% each)
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
    }

    @Test
    void handle_nullCommand_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_nullTurnOrder_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(null, LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_emptyTurnOrder_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(List.of(), LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_blankUsernameInTurnOrder_throwsIllegalArgument() {
        when(leagueAdminGuard.requireLeagueAdmin(any(), any())).thenReturn(new LeagueEntity());

        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(List.of("ash", "  "), LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void handle_duplicateUsernamesInTurnOrder_throwsIllegalArgument() {
        when(leagueAdminGuard.requireLeagueAdmin(any(), any())).thenReturn(new LeagueEntity());

        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(List.of("ash", "ASH"), LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void handle_activeDraftAlreadyExists_throwsIllegalState() {
        when(leagueAdminGuard.requireLeagueAdmin(any(), any())).thenReturn(new LeagueEntity());
        DraftEntity existing = new DraftEntity();
        existing.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(List.of("ash"), LEAGUE_ID, ADMIN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");
    }

    @Test
    void handle_notAdmin_throwsForbidden() {
        doThrow(new ForbiddenOperationException("not admin"))
                .when(leagueAdminGuard).requireLeagueAdmin(LEAGUE_ID, "brock");

        assertThatThrownBy(() -> handler.handle(new StartDraftCommand(List.of("ash"), LEAGUE_ID, "brock")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_validCommand_savesDraftWithCorrectState() {
        allowAdmin();

        handler.handle(new StartDraftCommand(List.of("ash", "Brock", " Misty "), LEAGUE_ID, ADMIN));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        DraftEntity saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(DraftStatus.IN_PROGRESS);
        assertThat(saved.getCurrentTurnIndex()).isZero();
        assertThat(saved.getCurrentRound()).isEqualTo(1);
        assertThat(saved.getTurnOrder()).containsExactly("ash", "Brock", "Misty");
        assertThat(saved.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(saved.getPicks()).isEmpty();
    }

    @Test
    void handle_trimsTurnOrderUsernames() {
        allowAdmin();

        handler.handle(new StartDraftCommand(List.of(" pikachu "), LEAGUE_ID, ADMIN));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().getTurnOrder()).containsExactly("pikachu");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(StartDraftCommand.class);
    }

    @Test
    void handle_assignsTiersOnDraftStart_quintiles() {
        allowAdminWithDefaultSettings();
        List<ClosedListEntity> pool = List.of(
                entityWithBst("id1", 620),
                entityWithBst("id2", 560),
                entityWithBst("id3", 500),
                entityWithBst("id4", 440),
                entityWithBst("id5", 380)
        );
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(pool);

        handler.handle(new StartDraftCommand(List.of(ADMIN), LEAGUE_ID, ADMIN));

        verify(closedListRepository).updateTier("id1", Tier.S);
        verify(closedListRepository).updateTier("id2", Tier.A);
        verify(closedListRepository).updateTier("id3", Tier.B);
        verify(closedListRepository).updateTier("id4", Tier.C);
        verify(closedListRepository).updateTier("id5", Tier.D);
    }

    @Test
    void handle_emptyPool_skipsTierAssignment() {
        allowAdmin();
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of());

        handler.handle(new StartDraftCommand(List.of(ADMIN), LEAGUE_ID, ADMIN));

        verify(closedListRepository, never()).updateTier(any(), any());
    }

    @Test
    void handle_assignsTiersOnDraftStart_customPercentages() {
        allowAdmin();

        // 10 Pokémon; configure S=40%, A=30%, B=20%, C=10%, D=0%
        // positions (i*100/10): 0,10,20,30,40,50,60,70,80,90
        // cum = [40,70,90,100]
        // i=0 pct=0  → S, i=1 pct=10 → S, i=2 pct=20 → S, i=3 pct=30 → S   (4 × S)
        // i=4 pct=40 → A, i=5 pct=50 → A, i=6 pct=60 → A                    (3 × A)
        // i=7 pct=70 → B, i=8 pct=80 → B                                     (2 × B)
        // i=9 pct=90 → C                                                      (1 × C)
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        LeagueSettings settings = LeagueSettings.builder()
                .tierPctS(40).tierPctA(30).tierPctB(20).tierPctC(10).tierPctD(0).build();
        league.setSettings(settings);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        List<ClosedListEntity> pool = List.of(
                entityWithBst("id01", 700), entityWithBst("id02", 680), entityWithBst("id03", 660),
                entityWithBst("id04", 640), entityWithBst("id05", 620), entityWithBst("id06", 600),
                entityWithBst("id07", 580), entityWithBst("id08", 560), entityWithBst("id09", 540),
                entityWithBst("id10", 520)
        );
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(pool);

        handler.handle(new StartDraftCommand(List.of(ADMIN), LEAGUE_ID, ADMIN));

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

    private static ClosedListEntity entityWithBst(String id, int bst) {
        ClosedListEntity e = new ClosedListEntity();
        e.setId(id);
        e.setStats(List.of(new Stat(bst, new StatData("total", ""))));
        return e;
    }
}
