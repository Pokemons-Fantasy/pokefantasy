package com.villu.pokefantasy.commands.pokemons.get;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mapper.PokemonMapper;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.response.PokemonsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetPokemonCommandHandler implements CommandHandler<GetPokemonCommand,GetPokemonCommandResponse> {


    private final CachePort cacheService;


    private final PokemonApiPort getPokemonApi;


    private final PokemonMapper pokemonMapper;

    private static final String CACHE_KEY = "all_pokemons";

    public GetPokemonCommandHandler(CachePort cacheService,PokemonApiPort pokemonApi, PokemonMapper pokemonMapper) {
        this.cacheService = cacheService;
        this.pokemonMapper = pokemonMapper;
        this.getPokemonApi = pokemonApi;
    }

    @Override
    public GetPokemonCommandResponse handle(GetPokemonCommand command) throws Exception {
        return getPokemonCacheById(command.id());
    }

    @Override
    public Class<GetPokemonCommand> commandType() {
        return GetPokemonCommand.class;
    }


    private GetPokemonCommandResponse getPokemonCacheById(int id) throws Exception {
        List<PokemonCacheDto> pokemonsResponse = cacheService.getPokemon(CACHE_KEY);

        return pokemonMapper.dtoToResponse(getDataFromPokemon(pokemonsResponse.stream()
                .filter(pokemon -> pokemon.getId() == id)
                .findFirst()
                .orElseThrow(() -> new Exception("Pokemon not found with id: " + id))));
    }

    private Pokemons getDataFromPokemon(PokemonCacheDto getPokemonsResponse) throws Exception {
        return getPokemonApi.fetchPokemonById(getPokemonsResponse.getUrl(), getPokemonsResponse.getName());
    }
}
