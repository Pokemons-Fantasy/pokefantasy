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
        l.setMembers(List.of(new LeagueMember(adminName, LeagueRole.ADMIN, 0)));
        return l;
    }

    private static UpdateLeagueSettingsCommand cmd(Integer coinsPerWin, Integer coinsPerLoss,
                                                    Integer s, Integer a, Integer b, Integer c, Integer d) {
        return new UpdateLeagueSettingsCommand("l1", coinsPerWin, coinsPerLoss, s, a, b, c, d, "ash");
    }

    private static UpdateLeagueSettingsCommand validCmd() {
        return cmd(200, 30, 500, 400, 300, 200, 100);
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
                new UpdateLeagueSettingsCommand("l1", 100, 50, 500, 400, 300, 200, 100, "brock")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_noDraft_throwsIllegalState() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(leagueWithAdmin("ash"));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(validCmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tras completar el draft");
    }

    @Test
    void handle_draftNotCompleted_throwsIllegalState() {
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(leagueWithAdmin("ash"));

        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(validCmd()))
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
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(UpdateLeagueSettingsCommand.class);
    }
}
