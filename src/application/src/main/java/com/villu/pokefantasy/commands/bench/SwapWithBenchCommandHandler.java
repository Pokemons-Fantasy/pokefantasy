package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SwapWithBenchCommandHandler implements CommandHandler<SwapWithBenchCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;

    public SwapWithBenchCommandHandler(DraftRepository draftRepository,
                                       ClosedListRepository closedListRepository,
                                       LeagueRepository leagueRepository,
                                       UserRepository userRepository) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(SwapWithBenchCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonToGive = command.pokemonToGive().trim();
        String pokemonToTake = command.pokemonToTake().trim();

        draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Swaps are only allowed after the draft is completed"));

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        boolean isMember = league.getMembers().stream()
                .anyMatch(m -> username.equals(m.getUsername()));
        if (!isMember) {
            throw new IllegalStateException("User is not a member of this league");
        }

        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        List<Pokemons> currentPokemons = user.getPokemons() != null ? new ArrayList<>(user.getPokemons()) : new ArrayList<>();

        Pokemons toGive = currentPokemons.stream()
                .filter(p -> leagueId.equals(p.getLeagueId()) && pokemonToGive.equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonToGive + "' is not in your team for this league"));

        ClosedListEntity benchEntry = closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(pokemonToTake, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonToTake + "' is not in the pool for this league"));

        Set<String> ownedNames = league.getMembers().stream()
                .map(member -> userRepository.findByUsername(member.getUsername()))
                .filter(u -> u != null && u.getPokemons() != null)
                .flatMap(u -> u.getPokemons().stream())
                .filter(p -> leagueId.equals(p.getLeagueId()))
                .map(p -> p.getName().toLowerCase())
                .collect(Collectors.toSet());

        if (ownedNames.contains(pokemonToTake.toLowerCase())) {
            throw new IllegalStateException("'" + pokemonToTake + "' is not available on the bench");
        }

        currentPokemons.remove(toGive);

        Pokemons newPokemon = new Pokemons();
        newPokemon.setId(benchEntry.getPokemonId());
        newPokemon.setName(benchEntry.getPokemonName());
        newPokemon.setStats(benchEntry.getStats());
        newPokemon.setTypes(benchEntry.getTypes());
        newPokemon.setLeagueId(leagueId);

        currentPokemons.add(newPokemon);
        user.setPokemons(currentPokemons);
        userRepository.updateUserWithPokemons(user);

        return null;
    }

    @Override
    public Class<SwapWithBenchCommand> commandType() {
        return SwapWithBenchCommand.class;
    }
}
