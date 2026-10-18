package com.villu.pokefantasy.league;

import com.villu.pokefantasy.commands.draft.DraftCompletionService;
import com.villu.pokefantasy.commands.draft.DraftTurnService;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los casos con draft los cubre RemoveMemberFromLeagueCommandHandlerTest, que usa este servicio real. */
class LeagueMemberRemovalTest {

    private final LeagueRepository leagueRepository = mock(LeagueRepository.class);
    private final DraftRepository draftRepository = mock(DraftRepository.class);
    private final ClosedListRepository closedListRepository = mock(ClosedListRepository.class);
    private final DraftCompletionService draftCompletionService = mock(DraftCompletionService.class);
    private final LeagueMemberRemoval removal = new LeagueMemberRemoval(
            leagueRepository, draftRepository, closedListRepository, new DraftTurnService(), draftCompletionService);

    @Test
    void remove_withoutActiveDraft_onlyRemovesTheMember() {
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(new ArrayList<>(List.of(
                new LeagueMember("ash", LeagueRole.ADMIN, 0), new LeagueMember("brock", LeagueRole.USER, 0))));
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.empty());

        removal.remove(league, "brock");

        verify(leagueRepository).removeMember("l1", "brock");
        verifyNoInteractions(closedListRepository, draftCompletionService);
    }
}
