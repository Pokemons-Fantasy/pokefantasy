package com.villu.pokefantasy.commands.pokemons.add;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.ResultPokemonDto;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.repository.PokemonRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class AddPokemonCommandHandler implements CommandHandler<AddPokemonCommand,Void> {


    private final CachePort cacheService;

    private static final String CACHE_KEY = "all_pokemons";

    private final PokemonMapper pokemonMapper;

    private final PokemonRepository pokemonRepository;

    public AddPokemonCommandHandler(CachePort cacheService, PokemonMapper pokemonMapper, PokemonRepository pokemonRepository) {
        this.pokemonMapper = pokemonMapper;
        this.cacheService = cacheService;
        this.pokemonRepository = pokemonRepository;
    }

    @Override
    public Void handle(AddPokemonCommand command) throws Exception {

        try {
            List<ResultPokemonDto> pokemons = cacheService.getPokemon(CACHE_KEY).stream().filter(pokemon ->
                    command.pokemonsNames().contains(pokemon.getName())).map(pokemonMapper::fromCacheDtoToResponse).toList();
            log.info("Saving pokemons to databasa: {}", pokemons);
            pokemonRepository.addPokemons(pokemonMapper.fromResultToEntity(pokemons));
        } catch (Exception e) {
            throw new Exception("Failed to add pokemons to cache: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Class<AddPokemonCommand> commandType() {
        return AddPokemonCommand.class;
    }
}
