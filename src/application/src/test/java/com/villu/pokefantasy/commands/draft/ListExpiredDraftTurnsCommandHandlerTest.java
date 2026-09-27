package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListExpiredDraftTurnsCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private LeagueRepository leagueRepository;

    private ListExpiredDraftTurnsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListExpiredDraftTurnsCommandHandler(draftRepository, leagueRepository);
    }

    private DraftEntity draft(String leagueId, Instant turnStartedAt) {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(leagueId);
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setCurrentTurnStartedAt(turnStartedAt);
        return draft;
    }

    private void league(String id, Integer timerSeconds) {
        LeagueEntity league = new LeagueEntity();
        league.setId(id);
        if (timerSeconds != null) {
            league.setSettings(LeagueSettings.builder().turnTimerSeconds(timerSeconds).build());
        }
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));
    }

    @Test
    void handle_returnsOnlyLeaguesWhoseTurnExpired() {
        Instant tenMinutesAgo = Instant.now().minusSeconds(600);
        when(draftRepository.findAllInProgress()).thenReturn(List.of(
                draft("expired", tenMinutesAgo),
                draft("running", Instant.now()),
                draft("no-timer", tenMinutesAgo),
                draft("default-settings", tenMinutesAgo),
                draft("no-start", null),
                draft("deleted-league", tenMinutesAgo)));
        league("expired", 60);
        league("running", 60);
        league("no-timer", 0);
        league("default-settings", null);
        league("no-start", 60);
        when(leagueRepository.findById("deleted-league")).thenReturn(Optional.empty());

        List<String> result = handler.handle(new ListExpiredDraftTurnsCommand());

        assertThat(result).containsExactly("expired");
    }

    @Test
    void handle_noDraftsInProgress_empty() {
        when(draftRepository.findAllInProgress()).thenReturn(List.of());

        assertThat(handler.handle(new ListExpiredDraftTurnsCommand())).isEmpty();
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(ListExpiredDraftTurnsCommand.class);
    }
}
