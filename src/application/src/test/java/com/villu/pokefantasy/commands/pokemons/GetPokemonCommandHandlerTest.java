package com.villu.pokefantasy.commands.pokemons;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommandHandler;
import com.villu.pokefantasy.commands.pokemons.get.GetPokemonCommandResponse;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.ports.PokemonApiPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPokemonCommandHandlerTest {

    @Mock private CachePort cachePort;
    @Mock private PokemonApiPort pokemonApiPort;
    @Mock private PokemonMapper pokemonMapper;

    private GetPokemonCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetPokemonCommandHandler(cachePort, pokemonApiPort, pokemonMapper);
    }

    @Test
    void handle_pokemonNotInCache_throwsException() {
        when(cachePort.getPokemon("all_pokemons")).thenReturn(List.of(
                new PokemonCacheDto("url", "bulbasaur", 1)));

        assertThatThrownBy(() -> handler.handle(new GetPokemonCommand(25)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("not found");
    }

    @Test
    void handle_pokemonFound_returnsResponse() throws Exception {
        PokemonCacheDto cachedPoke = new PokemonCacheDto("url/25", "pikachu", 25);
        when(cachePort.getPokemon("all_pokemons")).thenReturn(List.of(cachedPoke));

        Pokemons poke = new Pokemons(25, "pikachu", null, null, null, null, null, null);
        when(pokemonApiPort.fetchPokemonById("url/25", "pikachu")).thenReturn(poke);

        GetPokemonCommandResponse response = new GetPokemonCommandResponse();
        when(pokemonMapper.dtoToResponse(poke)).thenReturn(response);

        GetPokemonCommandResponse result = handler.handle(new GetPokemonCommand(25));

        assertThat(result).isSameAs(response);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetPokemonCommand.class);
    }
}
