package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateLeagueSettingsCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TierAssignmentService tierAssignmentService;

    private UpdateLeagueSettingsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateLeagueSettingsCommandHandler(
                leagueRepository, draftRepository, leagueAdminGuard, scheduleRepository, tierAssignmentService);
    }

    private LeagueEntity leagueWithAdmin(String adminName) {
        LeagueEntity l = new LeagueEntity();
        l.setId("l1");
        l.setMembers(List.of(new LeagueMember(adminName, LeagueRole.ADMIN, 0)));
        return l;
    }

    private static UpdateLeagueSettingsCommand cmd(Integer coinsPerWin, Integer coinsPerLoss,
                                                    Integer s, Integer a, Integer b, Integer c, Integer d) {
        return new UpdateLeagueSettingsCommand("l1", coinsPerWin, coinsPerLoss, s, a, b, c, d, null, null,
                20, 20, 20, 20, 20, null, "ash");
    }

    private static UpdateLeagueSettingsCommand validCmd() {
        return cmd(200, 30, 500, 400, 300, 200, 100);
    }

    private static UpdateLeagueSettingsCommand cmdWithPcts(Integer pctS, Integer pctA, Integer pctB,
                                                            Integer pctC, Integer pctD) {
        return new UpdateLeagueSettingsCommand("l1", 100, 50, 500, 400, 300, 200, 100, null, null,
                pctS, pctA, pctB, pctC, pctD, null, "ash");
    }

    @Test
    void handle_nullCoinsPerWin_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmd(null, 50, 500, 400, 300, 200, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void handle_nullCoinsPerLoss_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmd(100, null, 500, 400, 300, 200, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void handle_negativeCoinsPerWin_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmd(-5, 50, 500, 400, 300, 200, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void handle_negativeCoinsPerLoss_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmd(100, -1, 500, 400, 300, 200, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void handle_nullTierPrice_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmd(100, 50, null, 400, 300, 200, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void handle_negativeTierPrice_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmd(100, 50, 500, -10, 300, 200, 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void handle_notAdmin_propagatesForbidden() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "brock"))
                .thenThrow(new ForbiddenOperationException("not admin"));

        assertThatThrownBy(() -> handler.handle(
                new UpdateLeagueSettingsCommand("l1", 100, 50, 500, 400, 300, 200, 100, null, null, 20, 20, 20, 20, 20, null, "brock")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_noDraft_savesSettings() {
        LeagueEntity league = leagueWithAdmin("ash");
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        // Should not throw — settings can be edited when there is no draft yet
        handler.handle(validCmd());

        verify(leagueRepository).save(league);
    }

    @Test
    void handle_draftInProgress_throwsIllegalState() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(leagueWithAdmin("ash"));

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(validCmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("en curso");
    }

    @Test
    void handle_draftPending_savesSettings() {
        LeagueEntity league = leagueWithAdmin("ash");
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.PENDING);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(validCmd());

        verify(leagueRepository).save(league);
    }

    @Test
    void handle_tierPctSumNot100_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmdWithPcts(30, 30, 20, 10, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 100");
    }

    @Test
    void handle_nullTierPct_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmdWithPcts(null, 20, 20, 20, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void handle_negativeTierPct_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(cmdWithPcts(-10, 30, 30, 30, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void handle_validCommand_savesSettings() {
        LeagueEntity league = leagueWithAdmin("ash");
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(validCmd());

        ArgumentCaptor<LeagueEntity> captor = ArgumentCaptor.forClass(LeagueEntity.class);
        verify(leagueRepository).save(captor.capture());
        assertThat(captor.getValue().getSettings().getCoinsPerWin()).isEqualTo(200);
        assertThat(captor.getValue().getSettings().getCoinsPerLoss()).isEqualTo(30);
        assertThat(captor.getValue().getSettings().getPriceTierS()).isEqualTo(500);
        assertThat(captor.getValue().getSettings().getPriceTierA()).isEqualTo(400);
        assertThat(captor.getValue().getSettings().getPriceTierB()).isEqualTo(300);
        assertThat(captor.getValue().getSettings().getPriceTierC()).isEqualTo(200);
        assertThat(captor.getValue().getSettings().getPriceTierD()).isEqualTo(100);
        assertThat(captor.getValue().getSettings().getTierPctS()).isEqualTo(20);
        assertThat(captor.getValue().getSettings().getTierPctD()).isEqualTo(20);
    }

    @Test
    void handle_validCommand_recalculatesPoolTiers() {
        LeagueEntity league = leagueWithAdmin("ash");
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(validCmd());

        verify(tierAssignmentService).assignTiersToPool(eq("l1"), any(LeagueSettings.class));
    }

    @Test
    void handle_seasonStartDate_updatesJornadaDates() throws Exception {
        LeagueEntity league = leagueWithAdmin("ash");
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        // Build a schedule with 2 jornadas (no startDate yet)
        Match m1 = new Match("mid1", "ash", "brock", null, MatchStatus.PENDING);
        Jornada j1 = new Jornada(1, new ArrayList<>(List.of(m1)), null);
        Match m2 = new Match("mid2", "brock", "ash", null, MatchStatus.PENDING);
        Jornada j2 = new Jornada(2, new ArrayList<>(List.of(m2)), null);
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(j1, j2)));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        UpdateLeagueSettingsCommand cmd = new UpdateLeagueSettingsCommand(
                "l1", 100, 50, 500, 400, 300, 200, 100, "2026-06-06", 20, 20, 20, 20, 20, 20, null, "ash");
        handler.handle(cmd);

        // Schedule should be saved with updated dates
        ArgumentCaptor<ScheduleEntity> scheduleCaptor = ArgumentCaptor.forClass(ScheduleEntity.class);
        verify(scheduleRepository).save(scheduleCaptor.capture());
        ScheduleEntity saved = scheduleCaptor.getValue();
        assertThat(saved.getJornadas().get(0).getStartDate()).isEqualTo("2026-06-06");
        assertThat(saved.getJornadas().get(1).getStartDate()).isEqualTo("2026-06-13");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(UpdateLeagueSettingsCommand.class);
    }
}
