package com.villu.pokefantasy.commands.pokemons;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.commands.pokemons.available.GetAvailablePokemonsCommand;
import com.villu.pokefantasy.commands.pokemons.available.GetAvailablePokemonsCommandHandler;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.response.AvailablePokemonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailablePokemonsCommandHandlerTest {

    @Mock private CachePort cachePort;

    private GetAvailablePokemonsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetAvailablePokemonsCommandHandler(cachePort);
    }

    @Test
    void handle_emptyCache_returnsEmpty() {
        when(cachePort.getPokemon("pokemons")).thenReturn(List.of());

        assertThat(handler.handle(new GetAvailablePokemonsCommand())).isEmpty();
    }

    @Test
    void handle_withCachedPokemons_mapsCorrectly() {
        when(cachePort.getPokemon("pokemons")).thenReturn(List.of(
                new PokemonCacheDto("url", "pikachu", 25),
                new PokemonCacheDto("url2", "bulbasaur", 1)
        ));

        List<AvailablePokemonResponse> result = handler.handle(new GetAvailablePokemonsCommand());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(25);
        assertThat(result.get(0).getName()).isEqualTo("pikachu");
        assertThat(result.get(0).getSpriteUrl()).contains("25");
        assertThat(result.get(1).getId()).isEqualTo(1);
        assertThat(result.get(1).getName()).isEqualTo("bulbasaur");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetAvailablePokemonsCommand.class);
    }
}
