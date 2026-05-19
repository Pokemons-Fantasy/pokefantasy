package com.villu.pokefantasy.commands.pokemons;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.commands.pokemons.add.AddPokemonCommand;
import com.villu.pokefantasy.commands.pokemons.add.AddPokemonCommandHandler;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.repository.PokemonRepository;
import com.villu.pokefantasy.repository.entity.PokemonEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddPokemonCommandHandlerTest {

    @Mock private CachePort cachePort;
    @Mock private PokemonMapper pokemonMapper;
    @Mock private PokemonRepository pokemonRepository;

    private AddPokemonCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddPokemonCommandHandler(cachePort, pokemonMapper, pokemonRepository);
    }

    @Test
    void handle_cacheThrowsException_wrapsAndRethrows() {
        when(cachePort.getPokemon("all_pokemons")).thenThrow(new RuntimeException("cache error"));

        assertThatThrownBy(() -> handler.handle(new AddPokemonCommand(List.of("pikachu"))))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Failed to add pokemons");
    }

    @Test
    void handle_validCommand_savesFilteredPokemons() throws Exception {
        PokemonCacheDto poke = new PokemonCacheDto("url", "pikachu", 25);
        when(cachePort.getPokemon("all_pokemons")).thenReturn(List.of(poke));

        ResultPokemonDto dto = new ResultPokemonDto();
        when(pokemonMapper.fromCacheDtoToResponse(poke)).thenReturn(dto);

        PokemonEntity entity = new PokemonEntity();
        when(pokemonMapper.fromResultToEntity(List.of(dto))).thenReturn(List.of(entity));

        handler.handle(new AddPokemonCommand(List.of("pikachu")));

        verify(pokemonRepository).addPokemons(List.of(entity));
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(AddPokemonCommand.class);
    }
}
