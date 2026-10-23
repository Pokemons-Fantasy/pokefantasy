package com.villu.pokefantasy.league;

import com.villu.pokefantasy.commands.draft.DraftCompletionService;
import com.villu.pokefantasy.commands.draft.DraftTurnService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void remove_withCompletedDraft_returnsHisPicksToTheBench() {
        // Temporada en marcha: el draft ya terminó, pero sus Pokémon no pueden seguir siendo de quien se va.
        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(new ArrayList<>(List.of(
                new LeagueMember("ash", LeagueRole.ADMIN, 0), new LeagueMember("brock", LeagueRole.USER, 0))));
        DraftEntity draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, null, null, null),
                new DraftPick("brock", "onix", 95, 1, null, null, null))));
        when(draftRepository.findActiveByLeagueId("l1")).thenReturn(Optional.empty());
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        removal.remove(league, "brock");

        verify(draftRepository).save(draft);
        assertThat(draft.getPicks()).extracting(DraftPick::getUsername).containsExactly("ash");
        verifyNoInteractions(draftCompletionService);
    }
}
