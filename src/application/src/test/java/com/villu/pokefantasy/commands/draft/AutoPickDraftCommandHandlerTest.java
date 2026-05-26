package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoPickDraftCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private Mediator mediator;

    private AutoPickDraftCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";

    @BeforeEach
    void setUp() {
        handler = new AutoPickDraftCommandHandler(
                draftRepository, closedListRepository, leagueRepository, mediator);
    }

    private DraftEntity inProgressDraft(Instant turnStartedAt) {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(new ArrayList<>(List.of("ash", "brock")));
        draft.setCurrentTurnIndex(0);
        draft.setPicks(new ArrayList<>());
        draft.setCurrentTurnStartedAt(turnStartedAt);
        return draft;
    }

    private LeagueEntity leagueWithTimer(int timerSeconds) {
        LeagueEntity league = new LeagueEntity();
        LeagueSettings settings = LeagueSettings.builder()
                .coinsPerWin(100)
                .coinsPerLoss(50)
                .priceTierS(500).priceTierA(400).priceTierB(300).priceTierC(200).priceTierD(100)
                .tierPctS(20).tierPctA(20).tierPctB(20).tierPctC(20).tierPctD(20)
                .turnTimerSeconds(timerSeconds)
                .build();
        league.setSettings(settings);
        return league;
    }

    private ClosedListEntity closedListEntry(String pokemonName) {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonName(pokemonName);
        return entry;
    }

    @Test
    void handle_timerDisabled_throwsIllegalState() {
        DraftEntity draft = inProgressDraft(Instant.now().minusSeconds(120));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWithTimer(0)));

        assertThatThrownBy(() -> handler.handle(new AutoPickDraftCommand(LEAGUE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void handle_timerNotExpired_throwsIllegalState() {
        // turnStartedAt = now → deadline = now + 60s (in the future)
        DraftEntity draft = inProgressDraft(Instant.now());
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWithTimer(60)));

        assertThatThrownBy(() -> handler.handle(new AutoPickDraftCommand(LEAGUE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not expired");
    }

    @Test
    void handle_timerExpired_delegatesPickToMediator() throws Exception {
        // turnStartedAt = 10 seconds ago, timer = 5s → already expired
        DraftEntity draft = inProgressDraft(Instant.now().minusSeconds(10));
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "bulbasaur", 1, 1, Instant.now(), null, null)
        )));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWithTimer(5)));
        when(closedListRepository.findAllByLeagueId(LEAGUE_ID)).thenReturn(List.of(
                closedListEntry("pikachu"),
                closedListEntry("charmander"),
                closedListEntry("bulbasaur") // already picked → filtered out
        ));

        handler.handle(new AutoPickDraftCommand(LEAGUE_ID));

        ArgumentCaptor<DraftPickCommand> captor = ArgumentCaptor.forClass(DraftPickCommand.class);
        verify(mediator).send(captor.capture());
        DraftPickCommand sent = captor.getValue();
        assertThat(sent.username()).isEqualTo("ash");
        assertThat(sent.pokemonName()).isIn("pikachu", "charmander"); // not bulbasaur (already picked)
        assertThat(sent.leagueId()).isEqualTo(LEAGUE_ID);
    }
}
