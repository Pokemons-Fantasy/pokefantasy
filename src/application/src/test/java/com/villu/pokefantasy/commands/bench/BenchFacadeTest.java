package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.mediator.Mediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchFacadeTest {

    @Mock private Mediator mediator;

    private BenchFacade facade;

    @BeforeEach
    void setUp() {
        facade = new BenchFacade(mediator);
    }

    @Test
    void getBench_sendsGetBenchCommand() throws Exception {
        when(mediator.send(any(GetBenchCommand.class))).thenReturn(List.of());

        facade.getBench("l1");

        ArgumentCaptor<GetBenchCommand> captor = ArgumentCaptor.forClass(GetBenchCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
    }

    @Test
    void swap_sendsSwapWithBenchCommand() throws Exception {
        facade.swap("l1", "ash", "pikachu", "bulbasaur");

        ArgumentCaptor<SwapWithBenchCommand> captor = ArgumentCaptor.forClass(SwapWithBenchCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo("l1");
        assertThat(captor.getValue().username()).isEqualTo("ash");
        assertThat(captor.getValue().pokemonToGive()).isEqualTo("pikachu");
        assertThat(captor.getValue().pokemonToTake()).isEqualTo("bulbasaur");
    }
}
