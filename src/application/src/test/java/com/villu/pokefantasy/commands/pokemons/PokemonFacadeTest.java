package com.villu.pokefantasy.commands.pokemons;

import com.villu.pokefantasy.commands.pokemons.add.AddPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.available.GetAvailablePokemonsCommand;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommandResponse;
import com.villu.pokefantasy.commands.pokemons.saved.GetSavedPokemonsCommand;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.AvailablePokemonResponse;
import com.villu.pokefantasy.response.PokemonsResponse;
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
class PokemonFacadeTest {

    @Mock private Mediator mediator;
    @Mock private PokemonMapper pokemonMapper;

    private PokemonFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PokemonFacade(mediator, pokemonMapper);
    }

    @Test
    void getPokemon_sendsGetPokemonCommandAndMapsResult() throws Exception {
        GetPokemonCommandResponse cmdResponse = new GetPokemonCommandResponse();
        PokemonsResponse expected = new PokemonsResponse();
        when(mediator.send(any(GetPokemonCommand.class))).thenReturn(cmdResponse);
        when(pokemonMapper.commandToResponse(cmdResponse)).thenReturn(expected);

        PokemonsResponse result = facade.getPokemon(25);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<GetPokemonCommand> captor = ArgumentCaptor.forClass(GetPokemonCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(25);
    }

    @Test
    void addPokemons_sendsAddPokemonCommand() throws Exception {
        facade.addPokemons(List.of("pikachu", "bulbasaur"));

        ArgumentCaptor<AddPokemonCommand> captor = ArgumentCaptor.forClass(AddPokemonCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().pokemonsNames()).containsExactly("pikachu", "bulbasaur");
    }

    @Test
    void getSavedPokemons_sendsGetSavedPokemonsCommand() throws Exception {
        when(mediator.send(any(GetSavedPokemonsCommand.class))).thenReturn(List.of());

        facade.getSavedPokemons();

        verify(mediator).send(any(GetSavedPokemonsCommand.class));
    }

    @Test
    void getAvailablePokemons_sendsGetAvailablePokemonsCommand() throws Exception {
        when(mediator.send(any(GetAvailablePokemonsCommand.class))).thenReturn(List.of());

        List<AvailablePokemonResponse> result = facade.getAvailablePokemons();

        assertThat(result).isEmpty();
        verify(mediator).send(any(GetAvailablePokemonsCommand.class));
    }
}
