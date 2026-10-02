package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoteMemberToAdminCommandHandlerTest {

    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private LeagueRepository leagueRepository;

    private PromoteMemberToAdminCommandHandler handler;

    private static final String LEAGUE_ID = "l1";

    @BeforeEach
    void setUp() {
        handler = new PromoteMemberToAdminCommandHandler(leagueAdminGuard, leagueRepository);
    }

    private LeagueEntity league() {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(List.of(
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0)));
        return league;
    }

    @Test
    void handle_adminPromotesMember_savesMemberAsAdmin() {
        LeagueEntity league = league();
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, "ash")).thenReturn(league);

        handler.handle(new PromoteMemberToAdminCommand(LEAGUE_ID, "misty", "ash"));

        assertThat(league.getMembers().get(1).getLeagueRole()).isEqualTo(LeagueRole.ADMIN);
        verify(leagueRepository).save(league);
    }

    @Test
    void handle_targetAlreadyAdmin_changesNothing() {
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, "ash")).thenReturn(league());

        handler.handle(new PromoteMemberToAdminCommand(LEAGUE_ID, "ash", "ash"));

        verify(leagueRepository, never()).save(any());
    }

    @Test
    void handle_targetNotMember_throwsIllegalArgument() {
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, "ash")).thenReturn(league());

        assertThatThrownBy(() -> handler.handle(new PromoteMemberToAdminCommand(LEAGUE_ID, "gary", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es miembro");
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void handle_requesterNotAdmin_throwsForbidden() {
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, "misty"))
                .thenThrow(new ForbiddenOperationException("Solo el admin de la liga puede hacer esto"));

        assertThatThrownBy(() -> handler.handle(new PromoteMemberToAdminCommand(LEAGUE_ID, "misty", "misty")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(PromoteMemberToAdminCommand.class);
    }
}
