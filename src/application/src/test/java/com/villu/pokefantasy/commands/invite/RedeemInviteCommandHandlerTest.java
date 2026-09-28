package com.villu.pokefantasy.commands.invite;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.league.CurrentDraftService;
import com.villu.pokefantasy.repository.InviteRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedeemInviteCommandHandlerTest {

    @Mock private InviteRepository inviteRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private CurrentDraftService currentDraftService;

    private RedeemInviteCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RedeemInviteCommandHandler(inviteRepository, leagueRepository, currentDraftService);
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
        verify(leagueRepository, never()).addMember(anyString(), any());
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
    void handle_alreadyMember_returnsLeagueWithoutAddingOrCheckingDraft() {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                league("l1", new LeagueMember("ash", LeagueRole.USER, 0))));

        RedeemInviteResponse result = handler.handle(new RedeemInviteCommand("tok", "ash"));

        assertThat(result).isEqualTo(new RedeemInviteResponse("l1", true));
        verify(leagueRepository, never()).addMember(anyString(), any());
        verifyNoInteractions(currentDraftService);
    }

    @ParameterizedTest
    @EnumSource(value = DraftStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    void handle_draftAlreadyStarted_throwsIllegalState(DraftStatus status) {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                league("l1", new LeagueMember("existing", LeagueRole.ADMIN, 0))));
        when(currentDraftService.statusOf("l1")).thenReturn(Optional.of(status));

        assertThatThrownBy(() -> handler.handle(new RedeemInviteCommand("tok", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
        verify(leagueRepository, never()).addMember(anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(value = DraftStatus.class, names = {"PENDING", "CANCELLED"})
    void handle_draftNotStarted_addsMember(DraftStatus status) {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                league("l1", new LeagueMember("existing", LeagueRole.ADMIN, 0))));
        when(currentDraftService.statusOf("l1")).thenReturn(Optional.of(status));

        assertThat(handler.handle(new RedeemInviteCommand("tok", "ash")))
                .isEqualTo(new RedeemInviteResponse("l1", false));
        verify(leagueRepository).addMember(eq("l1"), any());
    }

    @Test
    void handle_validRedeem_addsMemberAsUser() {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                league("l1", new LeagueMember("existing", LeagueRole.ADMIN, 0))));
        when(currentDraftService.statusOf("l1")).thenReturn(Optional.empty());

        RedeemInviteResponse result = handler.handle(new RedeemInviteCommand("tok", "ash"));

        assertThat(result).isEqualTo(new RedeemInviteResponse("l1", false));

        ArgumentCaptor<LeagueMember> captor = ArgumentCaptor.forClass(LeagueMember.class);
        verify(leagueRepository).addMember(eq("l1"), captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("ash");
        assertThat(captor.getValue().getLeagueRole()).isEqualTo(LeagueRole.USER);
    }

    @Test
    void handle_sameTokenRedeemedByTwoUsers_bothJoin() {
        when(inviteRepository.findLeagueId("tok")).thenReturn("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                league("l1", new LeagueMember("existing", LeagueRole.ADMIN, 0))));
        when(currentDraftService.statusOf("l1")).thenReturn(Optional.empty());

        handler.handle(new RedeemInviteCommand("tok", "ash"));
        handler.handle(new RedeemInviteCommand("tok", "misty"));

        ArgumentCaptor<LeagueMember> captor = ArgumentCaptor.forClass(LeagueMember.class);
        verify(leagueRepository, times(2)).addMember(eq("l1"), captor.capture());
        assertThat(captor.getAllValues()).extracting(LeagueMember::getUsername).containsExactly("ash", "misty");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(RedeemInviteCommand.class);
    }
}
