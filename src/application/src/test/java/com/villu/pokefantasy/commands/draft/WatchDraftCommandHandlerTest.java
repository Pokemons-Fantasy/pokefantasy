package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchDraftCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;

    private WatchDraftCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WatchDraftCommandHandler(new LeagueMembershipGuard(leagueRepository));
    }

    private void leagueWithAsh() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.USER, 0))));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
    }

    @Test
    void handle_member_allowed() {
        leagueWithAsh();
        assertThatCode(() -> handler.handle(new WatchDraftCommand("l1", "ash"))).doesNotThrowAnyException();
    }

    @Test
    void handle_outsider_forbidden() {
        leagueWithAsh();
        assertThatThrownBy(() -> handler.handle(new WatchDraftCommand("l1", "gary")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(WatchDraftCommand.class);
    }
}
