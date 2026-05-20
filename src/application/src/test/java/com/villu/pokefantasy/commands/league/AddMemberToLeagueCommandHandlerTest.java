package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddMemberToLeagueCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;

    private AddMemberToLeagueCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddMemberToLeagueCommandHandler(leagueRepository, userRepository, leagueAdminGuard);
    }

    private LeagueEntity leagueWithMembers(LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(new ArrayList<>(List.of(members)));
        return league;
    }

    @Test
    void handle_userNotFound_throwsIllegalArgument() {
        LeagueEntity league = leagueWithMembers(new LeagueMember("ash", LeagueRole.ADMIN, 0));
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
        when(userRepository.findByUsername("brock")).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new AddMemberToLeagueCommand("l1", "brock", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brock");
    }

    @Test
    void handle_alreadyMember_throwsIllegalArgument() {
        LeagueEntity league = leagueWithMembers(
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0));
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
        when(userRepository.findByUsername("brock")).thenReturn(new UserEntity());

        assertThatThrownBy(() -> handler.handle(new AddMemberToLeagueCommand("l1", "brock", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void handle_validCommand_addsMember() {
        LeagueEntity league = leagueWithMembers(new LeagueMember("ash", LeagueRole.ADMIN, 0));
        when(leagueAdminGuard.requireLeagueAdmin("l1", "ash")).thenReturn(league);
        when(userRepository.findByUsername("brock")).thenReturn(new UserEntity());

        handler.handle(new AddMemberToLeagueCommand("l1", "brock", "ash"));

        var captor = forClass(LeagueMember.class);
        verify(leagueRepository).addMember(eq("l1"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUsername()).isEqualTo("brock");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getLeagueRole()).isEqualTo(LeagueRole.USER);
    }

    @Test
    void commandType_returnsCorrectClass() {
        org.assertj.core.api.Assertions.assertThat(handler.commandType())
                .isEqualTo(AddMemberToLeagueCommand.class);
    }
}
