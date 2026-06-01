package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.InviteRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedeemInviteCommandHandlerTest {

    @Mock private InviteRepository inviteRepository;
    @Mock private LeagueRepository leagueRepository;

    private RedeemInviteCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RedeemInviteCommandHandler(inviteRepository, leagueRepository);
    }

    private LeagueEntity league(String id, LeagueMember... members) {
        LeagueEntity l = new LeagueEntity();
        l.setId(id);
        l.setMembers(new ArrayList<>(List.of(members)));
        return l;
    }

    @Test
    void handle_invalidToken_throwsIllegalArgument() {
        when(inviteRepository.findLeagueId("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new RedeemInviteCommand("bad-token", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RedeemInviteCommand("tok", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_alreadyMember_throwsIllegalState() {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                league("l1", new LeagueMember("ash", LeagueRole.USER, 0))));

        assertThatThrownBy(() -> handler.handle(new RedeemInviteCommand("tok", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Already a member");
    }

    @Test
    void handle_validRedeem_addsMemberAndDeletesToken() {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                league("l1", new LeagueMember("existing", LeagueRole.ADMIN, 0))));

        String result = handler.handle(new RedeemInviteCommand("tok", "ash"));

        assertThat(result).isEqualTo("l1");

        ArgumentCaptor<LeagueMember> captor = ArgumentCaptor.forClass(LeagueMember.class);
        verify(leagueRepository).addMember(eq("l1"), captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("ash");
        assertThat(captor.getValue().getLeagueRole()).isEqualTo(LeagueRole.USER);

        verify(inviteRepository).delete("tok");
    }

    @Test
    void handle_invalidToken_doesNotAddMember() {
        when(inviteRepository.findLeagueId("bad")).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new RedeemInviteCommand("bad", "ash")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(leagueRepository, never()).addMember(eq("l1"), eq(null));
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(RedeemInviteCommand.class);
    }
}
