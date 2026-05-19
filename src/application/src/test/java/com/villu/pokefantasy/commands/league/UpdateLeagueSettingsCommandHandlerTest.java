package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateLeagueSettingsCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;

    private UpdateLeagueSettingsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateLeagueSettingsCommandHandler(leagueRepository, draftRepository, leagueAdminGuard);
    }

    private LeagueEntity leagueWithAdmin(String adminName) {
        LeagueEntity l = new LeagueEntity();
        l.setId("l1");
        l.setMembers(List.of(new LeagueMember(adminName, LeagueRole.ADMIN)));
        return l;
    }

    @Test
    void handle_nullCoinsPerWin_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new UpdateLeagueSettingsCommand("l1", null, 50, "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void handle_nullCoinsPerLoss_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new UpdateLeagueSettingsCommand("l1", 100, null, "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void handle_negativeCoinsPerWin_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new UpdateLeagueSettingsCommand("l1", -5, 50, "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void handle_negativeCoinsPerLoss_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new UpdateLeagueSettingsCommand("l1", 100, -1, "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void handle_notAdmin_propagatesForbidden() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "brock"))
                .thenThrow(new ForbiddenOperationException("not admin"));

        assertThatThrownBy(() -> handler.handle(new UpdateLeagueSettingsCommand("l1", 100, 50, "brock")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_noDraft_throwsIllegalState() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(leagueWithAdmin("ash"));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new UpdateLeagueSettingsCommand("l1", 100, 50, "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tras completar el draft");
    }

    @Test
    void handle_draftNotCompleted_throwsIllegalState() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(leagueWithAdmin("ash"));

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new UpdateLeagueSettingsCommand("l1", 100, 50, "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tras completar el draft");
    }

    @Test
    void handle_validCommand_savesSettings() {
        LeagueEntity league = leagueWithAdmin("ash");
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new UpdateLeagueSettingsCommand("l1", 200, 30, "ash"));

        ArgumentCaptor<LeagueEntity> captor = ArgumentCaptor.forClass(LeagueEntity.class);
        verify(leagueRepository).save(captor.capture());
        assertThat(captor.getValue().getSettings().getCoinsPerWin()).isEqualTo(200);
        assertThat(captor.getValue().getSettings().getCoinsPerLoss()).isEqualTo(30);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(UpdateLeagueSettingsCommand.class);
    }
}
