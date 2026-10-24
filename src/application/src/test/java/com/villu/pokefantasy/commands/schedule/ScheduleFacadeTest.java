package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.ports.ShowdownPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
