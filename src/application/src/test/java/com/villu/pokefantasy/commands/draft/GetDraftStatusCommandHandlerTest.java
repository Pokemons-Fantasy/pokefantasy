package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftConfig;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.response.DraftPickResponse;
import com.villu.pokefantasy.response.DraftStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDraftStatusCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private GetDraftStatusCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";

    @BeforeEach
    void setUp() {
        handler = new GetDraftStatusCommandHandler(draftRepository, leagueRepository, leagueMembershipGuard,
                new DraftTurnService());
    }

    @Test
    void handle_noDraftFound_throwsIllegalState() {
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene draft");
    }

    @Test
    void handle_activeDraft_currentTurnIsSet() {
        DraftEntity draft = buildDraft(DraftStatus.IN_PROGRESS, List.of("ash", "brock"), 0, 1, null);
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getCurrentTurn()).isEqualTo("ash");
        assertThat(response.getStatus()).isEqualTo(DraftStatus.IN_PROGRESS);
        assertThat(response.getTurnOrder()).containsExactly("ash", "brock");
        assertThat(response.getCurrentRound()).isEqualTo(1);
    }

    @Test
    void handle_completedDraft_currentTurnIsNull() {
        DraftEntity draft = buildDraft(DraftStatus.COMPLETED, List.of("ash"), 0, 10, null);
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getCurrentTurn()).isNull();
        assertThat(response.getStatus()).isEqualTo(DraftStatus.COMPLETED);
    }

    @Test
    void handle_nullPicks_returnsEmptyList() {
        DraftEntity draft = buildDraft(DraftStatus.IN_PROGRESS, List.of("ash"), 0, 1, null);
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getPicks()).isEmpty();
    }

    @Test
    void handle_withPicks_mapsPicksCorrectly() {
        Instant now = Instant.now();
        DraftPick pick = new DraftPick("ash", "pikachu", 25, 1, now, null, null);
        DraftEntity draft = buildDraft(DraftStatus.IN_PROGRESS, List.of("ash"), 0, 1, List.of(pick));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getPicks()).hasSize(1);
        assertThat(response.getPicks().get(0).getUsername()).isEqualTo("ash");
        assertThat(response.getPicks().get(0).getPokemonName()).isEqualTo("pikachu");
        assertThat(response.getPicks().get(0).getPokemonId()).isEqualTo(25);
        assertThat(response.getPicks().get(0).getRound()).isEqualTo(1);
        assertThat(response.getPicks().get(0).getPickedAt()).isEqualTo(now);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetDraftStatusCommand.class);
    }

    private DraftEntity buildDraft(DraftStatus status, List<String> turnOrder,
                                   int turnIndex, int round, List<DraftPick> picks) {
        DraftEntity draft = new DraftEntity();
        draft.setId("draft-1");
        draft.setStatus(status);
        draft.setTurnOrder(turnOrder);
        draft.setCurrentTurnIndex(turnIndex);
        draft.setCurrentRound(round);
        draft.setPicks(picks);
        draft.setLeagueId(LEAGUE_ID);
        return draft;
    }

    @Test
    void handle_budgetDraft_returnsConfigBudgetsAndPrices() {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.PENDING);
        draft.setTurnOrder(List.of("ash", "misty"));
        draft.setConfig(DraftConfig.defaults());
        DraftPick paid = new DraftPick("ash", "mew", 151, 1, Instant.now(), null, null);
        paid.setPrice(200);
        draft.setPicks(List.of(paid));
        draft.setDraftHistory(List.of(paid));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getConfig()).isEqualTo(DraftConfig.defaults());
        assertThat(response.getBudgets()).containsExactly(Map.entry("ash", 800), Map.entry("misty", 1000));
        assertThat(response.getDraftHistory()).extracting(DraftPickResponse::getPrice).containsExactly(200);
        assertThat(response.getCurrentTurn()).isNull(); // en preparación no hay turno
    }

    @Test
    void handle_draftWithoutConfig_hasNoBudgets() {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setTurnOrder(List.of("ash"));
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        DraftStatusResponse response = handler.handle(new GetDraftStatusCommand(LEAGUE_ID, "ash"));

        assertThat(response.getConfig()).isNull();
        assertThat(response.getBudgets()).isNull();
    }
}
