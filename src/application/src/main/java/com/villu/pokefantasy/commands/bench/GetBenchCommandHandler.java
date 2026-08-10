package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.league.TierPricingService;
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
    private final LeagueMembershipGuard leagueMembershipGuard;
    private final TierPricingService tierPricingService;

    public GetBenchCommandHandler(ClosedListRepository closedListRepository,
                                  LeagueRepository leagueRepository,
                                  UserRepository userRepository,
                                  LeagueMembershipGuard leagueMembershipGuard,
                                  TierPricingService tierPricingService) {
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.tierPricingService = tierPricingService;
    }

    @Override
    public List<BenchEntryResponse> handle(GetBenchCommand command) {
        String leagueId = command.leagueId();
        leagueMembershipGuard.requireMember(leagueId, command.requestingUsername());

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
                    int price = tierPricingService.priceForTier(settings, entry.getTier());
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

    @Override
    public Class<GetBenchCommand> commandType() {
        return GetBenchCommand.class;
    }
}
