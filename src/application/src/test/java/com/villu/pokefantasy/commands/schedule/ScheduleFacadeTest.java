package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.ports.ShowdownPort;
import com.villu.pokefantasy.ports.ShowdownReplay;
import com.villu.pokefantasy.ports.ShowdownReplaySummary;
import com.villu.pokefantasy.response.ReplayCandidateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleFacadeTest {

    @Mock private Mediator mediator;
    @Mock private ShowdownPort showdownPort;

    private ScheduleFacade facade;

    @BeforeEach
    void setUp() {
        facade = new ScheduleFacade(mediator, showdownPort);
    }

    private static final ReplayImportContext CTX = new ReplayImportContext("ash", "misty", "ash", "misty",
            "gen9doublescustomgame", 1000L, java.util.Set.of(), null, null, 3, null);

    private static ShowdownReplay replay(String id, long uploadtime) {
        return new ShowdownReplay(id, "gen9doublescustomgame", List.of("Ash", "Misty"),
                "|player|p1|Ash|a|\n|player|p2|Misty|b|\n|win|Ash", uploadtime);
    }

    @Test
    void replayCandidates_searchesThisWeekOnlyAndEvaluates() throws Exception {
        when(mediator.send(new PrepareReplayImportCommand("l1", "m1", "ash"))).thenReturn(CTX);
        when(showdownPort.searchReplays("ash", "misty")).thenReturn(List.of(
                new ShowdownReplaySummary("new", List.of("Ash", "Misty"), 2000L),
                new ShowdownReplaySummary("old", List.of("Ash", "Misty"), 500L)));
        when(showdownPort.getReplay("new")).thenReturn(Optional.of(replay("new", 2000L)));

        List<ReplayCandidateResponse> out = facade.replayCandidates("l1", "m1", null, "ash");

        assertThat(out).extracting(ReplayCandidateResponse::getId).containsExactly("new");
        assertThat(out.getFirst().isSelectable()).isTrue();
        verify(showdownPort, never()).getReplay("old");
    }

    @Test
    void replayCandidates_pastedUrl_readsOnlyThatReplay() throws Exception {
        when(mediator.send(new PrepareReplayImportCommand("l1", "m1", "ash"))).thenReturn(CTX);
        when(showdownPort.getReplay("gen9doublescustomgame-9-secretpw")).thenReturn(
                Optional.of(replay("gen9doublescustomgame-9-secretpw", 3000L)));

        List<ReplayCandidateResponse> out = facade.replayCandidates("l1", "m1",
                "https://replay.pokemonshowdown.com/gen9doublescustomgame-9-secretpw", "ash");

        assertThat(out).hasSize(1);
        verify(showdownPort, never()).searchReplays(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void replayCandidates_badUrl_badRequest() throws Exception {
        when(mediator.send(new PrepareReplayImportCommand("l1", "m1", "ash"))).thenReturn(CTX);

        assertThatThrownBy(() -> facade.replayCandidates("l1", "m1", "https://evil.example/x", "ash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pega el enlace de un replay de Showdown.");
    }

    @Test
    void replayCandidates_pastedReplayMissing_badRequest() throws Exception {
        when(mediator.send(new PrepareReplayImportCommand("l1", "m1", "ash"))).thenReturn(CTX);
        when(showdownPort.getReplay("gen9doublescustomgame-9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.replayCandidates("l1", "m1",
                "https://replay.pokemonshowdown.com/gen9doublescustomgame-9.json", "ash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se encontró el replay gen9doublescustomgame-9.");
    }

    @Test
    void importSeries_redownloadsEveryReplayAndSendsThem() throws Exception {
        ShowdownReplay g1 = replay("g1", 2000L);
        ShowdownReplay g2 = replay("g2", 2001L);
        when(showdownPort.getReplay("g1")).thenReturn(Optional.of(g1));
        when(showdownPort.getReplay("g2")).thenReturn(Optional.of(g2));

        facade.importSeries("l1", "m1", List.of("g1", "g2"), "ash");

        verify(mediator).send(new ImportSeriesCommand("l1", "m1", "ash", List.of(g1, g2)));
    }

    @Test
    void importSeries_invalidId_rejectedBeforeDownloading() {
        assertThatThrownBy(() -> facade.importSeries("l1", "m1", List.of("g1", "../../x"), "ash"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Replay no válido.");
        java.util.List<String> withNull = new java.util.ArrayList<>(List.of("g1"));
        withNull.add(null);
        assertThatThrownBy(() -> facade.importSeries("l1", "m1", withNull, "ash"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Replay no válido.");
        verifyNoInteractions(showdownPort);
    }

    @Test
    void importSeries_limits() {
        assertThatThrownBy(() -> facade.importSeries("l1", "m1", List.of(), "ash"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Marca las partidas de la serie.");
        assertThatThrownBy(() -> facade.importSeries("l1", "m1", List.of("a", "b", "c", "d"), "ash"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Marca como mucho 3 partidas.");
        verifyNoInteractions(showdownPort);
    }

    @Test
    void declareLineup_pokepaste_passesWarnings() throws Exception {
        when(showdownPort.getPokepaste("abc123")).thenReturn(Optional.of("Pikachu\n\nRaichu"));

        facade.declareLineup("l1", "m1", List.of("pikachu", "garchomp"), "https://pokepast.es/abc123", "ash");

        verify(mediator).send(new DeclareLineupCommand("l1", "m1", "ash", List.of("pikachu", "garchomp"),
                "https://pokepast.es/abc123",
                List.of("En el paste falta garchomp.", "El paste tiene Raichu, que no está en la alineación.")));
    }

    @Test
    void declareLineup_otherUrl_doesNotFetch() throws Exception {
        facade.declareLineup("l1", "m1", List.of("pikachu"), "https://example.com/team", "ash");

        verifyNoInteractions(showdownPort);
        verify(mediator).send(new DeclareLineupCommand("l1", "m1", "ash", List.of("pikachu"),
                "https://example.com/team", List.of()));
    }

    @Test
    void declareLineup_pasteUnreadable_warns() throws Exception {
        when(showdownPort.getPokepaste("abc123")).thenThrow(new ShowdownUnavailableException("x", null));

        facade.declareLineup("l1", "m1", List.of("pikachu"), "https://pokepast.es/abc123", "ash");

        verify(mediator).send(new DeclareLineupCommand("l1", "m1", "ash", List.of("pikachu"),
                "https://pokepast.es/abc123", List.of("No se pudo leer el paste.")));
    }

    @Test
    void declareLineup_pasteNotFound_warns() throws Exception {
        when(showdownPort.getPokepaste("abc123")).thenReturn(Optional.empty());

        facade.declareLineup("l1", "m1", List.of("pikachu"), "https://pokepast.es/abc123", "ash");

        verify(mediator).send(new DeclareLineupCommand("l1", "m1", "ash", List.of("pikachu"),
                "https://pokepast.es/abc123", List.of("No se pudo leer el paste.")));
    }

    @Test
    void correctResult_sendsCommandWithNewWinner() throws Exception {
        facade.correctResult("l1", "m1", "brock", new MatchScore(3, 1), "ash");

        verify(mediator).send(new CorrectMatchResultCommand("l1", "m1", "brock", new MatchScore(3, 1), "ash"));
    }

    @Test
    void revertResult_sendsCommandWithoutWinner() throws Exception {
        facade.revertResult("l1", "m1", "ash");

        verify(mediator).send(new CorrectMatchResultCommand("l1", "m1", null, "ash"));
    }

    @Test
    void recordResult_sendsCommandWithScore() throws Exception {
        facade.recordResult("l1", "m1", "brock", null, "ash");

        verify(mediator).send(new RecordMatchResultCommand("l1", "m1", "brock", null, "ash"));
    }

    @Test
    void windowReminders_sendListAndSendCommands() throws Exception {
        when(mediator.send(new ListDueWindowRemindersCommand())).thenReturn(List.of("l1"));
        when(mediator.send(new SendWindowRemindersCommand("l1"))).thenReturn(true);

        assertThat(facade.leaguesWithDueWindowReminders()).containsExactly("l1");
        assertThat(facade.sendWindowReminders("l1")).isTrue();
    }
}
