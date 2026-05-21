package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.response.BenchEntryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GetBenchCommandHandler implements CommandHandler<GetBenchCommand, List<BenchEntryResponse>> {

    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;

    public GetBenchCommandHandler(ClosedListRepository closedListRepository,
                                  LeagueRepository leagueRepository,
                                  UserRepository userRepository) {
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<BenchEntryResponse> handle(GetBenchCommand command) {
        String leagueId = command.leagueId();

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        Set<String> ownedPokemonNames = league.getMembers().stream()
                .map(member -> userRepository.findByUsername(member.getUsername()))
                .filter(user -> user != null && user.getPokemons() != null)
                .flatMap(user -> user.getPokemons().stream())
                .filter(p -> leagueId.equals(p.getLeagueId()))
                .map(p -> p.getName().toLowerCase())
                .collect(Collectors.toSet());

        LeagueSettings settings = league.getSettings();

        return closedListRepository.findAllByLeagueId(leagueId).stream()
                .filter(entry -> !ownedPokemonNames.contains(entry.getPokemonName().toLowerCase()))
                .map(entry -> {
                    int price = priceForTier(settings, entry.getTier());
                    return BenchEntryResponse.builder()
                            .pokemonId(entry.getPokemonId())
                            .pokemonName(entry.getPokemonName())
                            .sprite(entry.getSprite())
                            .tier(entry.getTier() != null ? entry.getTier().name() : null)
                            .price(price)
                            .build();
                })
                .toList();
    }

    private int priceForTier(LeagueSettings settings, Tier tier) {
        if (settings == null || tier == null) return 0;
        return switch (tier) {
            case S -> settings.getPriceTierS() != null ? settings.getPriceTierS() : 0;
            case A -> settings.getPriceTierA() != null ? settings.getPriceTierA() : 0;
            case B -> settings.getPriceTierB() != null ? settings.getPriceTierB() : 0;
            case C -> settings.getPriceTierC() != null ? settings.getPriceTierC() : 0;
            case D -> settings.getPriceTierD() != null ? settings.getPriceTierD() : 0;
        };
    }

    @Override
    public Class<GetBenchCommand> commandType() {
        return GetBenchCommand.class;
    }
}
