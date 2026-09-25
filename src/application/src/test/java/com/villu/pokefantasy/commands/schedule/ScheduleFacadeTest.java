package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

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
        facade.correctResult("l1", "m1", "brock", "ash");

        verify(mediator).send(new CorrectMatchResultCommand("l1", "m1", "brock", "ash"));
    }

    @Test
    void revertResult_sendsCommandWithoutWinner() throws Exception {
        facade.revertResult("l1", "m1", "ash");

        verify(mediator).send(new CorrectMatchResultCommand("l1", "m1", null, "ash"));
    }
}
