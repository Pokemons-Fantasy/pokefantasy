package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleFacadeTest {

    @Mock private Mediator mediator;

    private ScheduleFacade facade;

    @BeforeEach
    void setUp() {
        facade = new ScheduleFacade(mediator);
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
