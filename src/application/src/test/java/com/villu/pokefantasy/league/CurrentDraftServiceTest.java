package com.villu.pokefantasy.league;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentDraftServiceTest {

    @Mock private DraftRepository draftRepository;

    private CurrentDraftService service;

    @BeforeEach
    void setUp() {
        service = new CurrentDraftService(draftRepository);
    }

    @Test
    void statusByLeague_noLeagues_doesNotQuery() {
        assertThat(service.statusByLeague(List.of())).isEmpty();
        verifyNoInteractions(draftRepository);
    }

    @Test
    void statusByLeague_prefersActiveDraftOverNewerFinishedOne() {
        // Orden del repositorio: más reciente primero
        when(draftRepository.findAllByLeagueIdsNewestFirst(List.of("l1")))
                .thenReturn(List.of(draft("l1", DraftStatus.CANCELLED), draft("l1", DraftStatus.IN_PROGRESS)));

        assertThat(service.statusByLeague(List.of("l1"))).containsExactlyEntriesOf(Map.of("l1", DraftStatus.IN_PROGRESS));
    }

    @Test
    void statusByLeague_withoutActiveDraft_usesLatest() {
        when(draftRepository.findAllByLeagueIdsNewestFirst(List.of("l1", "l2", "l3")))
                .thenReturn(List.of(
                        draft("l1", DraftStatus.COMPLETED),
                        draft("l2", DraftStatus.PENDING),
                        draft("l1", DraftStatus.CANCELLED)));

        assertThat(service.statusByLeague(List.of("l1", "l2", "l3")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("l1", DraftStatus.COMPLETED, "l2", DraftStatus.PENDING));
    }

    @Test
    void statusOf_returnsStatusOfCurrentDraft() {
        when(draftRepository.findAllByLeagueIdsNewestFirst(List.of("l1")))
                .thenReturn(List.of(draft("l1", DraftStatus.COMPLETED)));

        assertThat(service.statusOf("l1")).contains(DraftStatus.COMPLETED);
    }

    @Test
    void statusOf_leagueWithoutDraft_isEmpty() {
        when(draftRepository.findAllByLeagueIdsNewestFirst(List.of("l1"))).thenReturn(List.of());

        assertThat(service.statusOf("l1")).isEmpty();
    }

    private static DraftEntity draft(String leagueId, DraftStatus status) {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(leagueId);
        draft.setStatus(status);
        return draft;
    }
}
