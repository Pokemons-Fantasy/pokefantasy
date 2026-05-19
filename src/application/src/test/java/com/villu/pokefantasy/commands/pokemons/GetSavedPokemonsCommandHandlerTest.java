package com.villu.pokefantasy.commands.pokemons;

import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommandResponse;
import com.villu.pokefantasy.commands.pokemons.saved.GetSavedPokemonsCommand;
import com.villu.pokefantasy.commands.pokemons.saved.GetSavedPokemonsCommandHandler;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.repository.PokemonRepository;
import com.villu.pokefantasy.repository.entity.PokemonEntity;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSavedPokemonsCommandHandlerTest {

    @Mock private PokemonMapper pokemonMapper;
    @Mock private PokemonRepository pokemonRepository;
    @Mock private PokemonApiPort pokemonApiPort;

    private GetSavedPokemonsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetSavedPokemonsCommandHandler(pokemonMapper, pokemonRepository, pokemonApiPort);
    }

    @Test
    void handle_emptyRepository_returnsEmptyList() throws Exception {
        when(pokemonRepository.getAllPokemons()).thenReturn(List.of());

        List<PokemonsResponse> result = handler.handle(new GetSavedPokemonsCommand());

        assertThat(result).isEmpty();
    }

    @Test
    void handle_fetchThrowsException_skipsAndReturnsPartial() throws Exception {
        PokemonEntity entity = new PokemonEntity();
        entity.setName("pikachu");
        entity.setUrl("url/25");
        when(pokemonRepository.getAllPokemons()).thenReturn(List.of(entity));
        when(pokemonApiPort.fetchPokemonById("url/25", "pikachu")).thenThrow(new RuntimeException("API down"));

        List<PokemonsResponse> result = handler.handle(new GetSavedPokemonsCommand());

        assertThat(result).isEmpty();
    }

    @Test
    void handle_withSavedPokemons_returnsMappedList() throws Exception {
        PokemonEntity entity = new PokemonEntity();
        entity.setName("pikachu");
        entity.setUrl("url/25");
        when(pokemonRepository.getAllPokemons()).thenReturn(List.of(entity));

        Pokemons poke = new Pokemons(25, "pikachu", null, null, null, null, null, null);
        when(pokemonApiPort.fetchPokemonById("url/25", "pikachu")).thenReturn(poke);

        GetPokemonCommandResponse cmdResponse = new GetPokemonCommandResponse();
        when(pokemonMapper.dtoToResponse(poke)).thenReturn(cmdResponse);

        PokemonsResponse expectedResponse = new PokemonsResponse();
        when(pokemonMapper.commandToResponse(cmdResponse)).thenReturn(expectedResponse);

        List<PokemonsResponse> result = handler.handle(new GetSavedPokemonsCommand());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(expectedResponse);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetSavedPokemonsCommand.class);
    }
}
