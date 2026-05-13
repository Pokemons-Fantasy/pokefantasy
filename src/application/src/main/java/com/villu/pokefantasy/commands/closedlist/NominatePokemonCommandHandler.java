package com.villu.pokefantasy.commands.closedlist;

import com.villu.pokefantasy.cache.dto.PokemonCacheDto;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.ports.CachePort;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NominatePokemonCommandHandler implements CommandHandler<NominatePokemonCommand, Void> {

    private static final int MAX_NOMINATIONS_PER_USER = 16;

    private final ClosedListRepository closedListRepository;
    private final CachePort cachePort;
    private final PokemonApiPort pokemonApiPort;

    public NominatePokemonCommandHandler(ClosedListRepository closedListRepository,
                                         CachePort cachePort,
                                         PokemonApiPort pokemonApiPort) {
        this.closedListRepository = closedListRepository;
        this.cachePort = cachePort;
        this.pokemonApiPort = pokemonApiPort;
    }

    @Override
    public Void handle(NominatePokemonCommand command) throws Exception {
        if (command.username() == null || command.pokemonName() == null
                || command.username().isBlank() || command.pokemonName().isBlank()) {
            throw new IllegalArgumentException("Username and pokemonName are required");
        }

        if (closedListRepository.existsByPokemonName(command.pokemonName().toLowerCase())) {
            throw new IllegalArgumentException("Pokemon '" + command.pokemonName() + "' is already in the closed list");
        }

        long nominations = closedListRepository.countByNominatedBy(command.username());
        if (nominations >= MAX_NOMINATIONS_PER_USER) {
            throw new IllegalArgumentException("User has reached the maximum of " + MAX_NOMINATIONS_PER_USER + " nominations");
        }

        List<PokemonCacheDto> cachedList = cachePort.getPokemon("pokemons");
        PokemonCacheDto cached = cachedList.stream()
                .filter(p -> p.getName().equalsIgnoreCase(command.pokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pokemon '" + command.pokemonName() + "' not found"));

        Pokemons pokemon = pokemonApiPort.fetchPokemonById(cached.getUrl(), cached.getName());

        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonId(pokemon.getId());
        entry.setPokemonName(pokemon.getName());
        entry.setStats(pokemon.getStats());
        entry.setTypes(pokemon.getTypes());
        entry.setNominatedBy(command.username());

        closedListRepository.save(entry);
        return null;
    }

    @Override
    public Class<NominatePokemonCommand> commandType() {
        return NominatePokemonCommand.class;
    }
}
