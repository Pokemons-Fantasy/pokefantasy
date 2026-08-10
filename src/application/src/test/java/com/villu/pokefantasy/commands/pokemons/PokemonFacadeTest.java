package com.villu.pokefantasy.commands.pokemons;

import com.villu.pokefantasy.commands.pokemons.available.GetAvailablePokemonsCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.AvailablePokemonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PokemonFacadeTest {

    @Mock private Mediator mediator;

    private PokemonFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PokemonFacade(mediator);
    }

    @Test
    void getAvailablePokemons_sendsGetAvailablePokemonsCommand() throws Exception {
        when(mediator.send(any(GetAvailablePokemonsCommand.class))).thenReturn(List.of());

        List<AvailablePokemonResponse> result = facade.getAvailablePokemons();

        assertThat(result).isEmpty();
        verify(mediator).send(any(GetAvailablePokemonsCommand.class));
    }
}
