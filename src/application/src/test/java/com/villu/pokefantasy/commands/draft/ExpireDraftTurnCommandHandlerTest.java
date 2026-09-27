package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpireDraftTurnCommandHandlerTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftTurnTimeoutService draftTurnTimeoutService;

    private ExpireDraftTurnCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExpireDraftTurnCommandHandler(leagueRepository, draftTurnTimeoutService);
    }

    @Test
    void handle_autoPicksWithoutMembershipCheck() throws Exception {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        handler.handle(new ExpireDraftTurnCommand("l1"));

        verify(draftTurnTimeoutService).autoPickExpiredTurn(league);
    }

    @Test
    void handle_leagueNotFound_throws() {
        when(leagueRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ExpireDraftTurnCommand("gone")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(draftTurnTimeoutService);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(ExpireDraftTurnCommand.class);
    }
}
