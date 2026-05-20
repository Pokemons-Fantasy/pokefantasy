package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveMemberFromLeagueCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;

    private RemoveMemberFromLeagueCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RemoveMemberFromLeagueCommandHandler(leagueRepository, draftRepository);
    }

    private LeagueEntity leagueWith(String id, LeagueMember... members) {
        LeagueEntity l = new LeagueEntity();
        l.setId(id);
        l.setMembers(new ArrayList<>(List.of(members)));
        return l;
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(leagueRepository.findById("l1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_nonAdminRemovingOther_throwsForbidden() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0),
                new LeagueMember("misty", LeagueRole.USER, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "misty")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void handle_targetNotMember_throwsIllegalArgument() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void handle_removeLastAdmin_throwsIllegalState() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        // ash trying to leave (self-leave of last admin)
        assertThatThrownBy(() -> handler.handle(new RemoveMemberFromLeagueCommand("l1", "ash", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last admin");
    }

    @Test
    void handle_selfLeave_removesSuccessfully() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.empty());

        handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "brock"));

        verify(leagueRepository).removeMember("l1", "brock");
    }

    @Test
    void handle_adminRemovesMember_removesSuccessfully() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.empty());

        handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "ash"));

        verify(leagueRepository).removeMember("l1", "brock");
    }

    @Test
    void handle_withActiveDraft_removesPlayerFromDraft() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        DraftEntity draft = new DraftEntity();
        draft.setTurnOrder(new ArrayList<>(List.of("ash", "brock")));
        draft.setCurrentTurnIndex(0);
        DraftPick pick = new DraftPick();
        pick.setUsername("brock");
        draft.setPicks(new ArrayList<>(List.of(pick)));
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "ash"));

        verify(draftRepository).save(draft);
        assertThat(draft.getTurnOrder()).doesNotContain("brock");
        assertThat(draft.getPicks()).isEmpty();
    }

    @Test
    void handle_withActiveDraft_playerNotInTurnOrder_stillRemoves() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        DraftEntity draft = new DraftEntity();
        draft.setTurnOrder(new ArrayList<>(List.of("ash")));
        draft.setCurrentTurnIndex(0);
        draft.setPicks(new ArrayList<>());
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "ash"));

        verify(draftRepository).save(draft);
        // turnOrder unchanged since brock wasn't in it
        assertThat(draft.getTurnOrder()).containsExactly("ash");
    }

    @Test
    void handle_withActiveDraft_removedIndexBeforeCurrent_adjustsIndex() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        DraftEntity draft = new DraftEntity();
        // brock at index 0, current turn is ash at index 1
        draft.setTurnOrder(new ArrayList<>(List.of("brock", "ash")));
        draft.setCurrentTurnIndex(1);
        draft.setPicks(new ArrayList<>());
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "ash"));

        // removedIndex(0) < currentIndex(1), so currentTurnIndex should be 0
        assertThat(draft.getCurrentTurnIndex()).isEqualTo(0);
        assertThat(draft.getTurnOrder()).containsExactly("ash");
    }

    @Test
    void handle_withActiveDraft_removedIndexAtCurrent_wrapsIndex() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        DraftEntity draft = new DraftEntity();
        // ash at index 0, brock at index 1 (current)
        draft.setTurnOrder(new ArrayList<>(List.of("ash", "brock")));
        draft.setCurrentTurnIndex(1);
        draft.setPicks(new ArrayList<>());
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.of(draft));

        handler.handle(new RemoveMemberFromLeagueCommand("l1", "brock", "ash"));

        // removedIndex(1) == currentIndex(1), new index = 1 % 1 = 0
        assertThat(draft.getCurrentTurnIndex()).isEqualTo(0);
        assertThat(draft.getTurnOrder()).containsExactly("ash");
    }

    @Test
    void handle_multipleAdmins_canRemoveOneAdmin() {
        LeagueEntity league = leagueWith("l1",
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.ADMIN, 0));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.empty());

        // misty is admin and leaves self
        handler.handle(new RemoveMemberFromLeagueCommand("l1", "misty", "misty"));

        verify(leagueRepository).removeMember("l1", "misty");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(RemoveMemberFromLeagueCommand.class);
    }
}
