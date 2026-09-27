package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.team.TeamOperation;
import com.villu.pokefantasy.team.TeamTransferService;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.springframework.stereotype.Service;

import java.time.Instant;


@Service
public class SwapWithBenchCommandHandler implements CommandHandler<SwapWithBenchCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final TeamTransferService teamTransferService;
    private final ActivityEventRepository activityEventRepository;
    private final TierPricingService tierPricingService;

    public SwapWithBenchCommandHandler(DraftRepository draftRepository,
                                       ClosedListRepository closedListRepository,
                                       LeagueRepository leagueRepository,
                                       TeamTransferService teamTransferService,
                                       ActivityEventRepository activityEventRepository,
                                       TierPricingService tierPricingService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.teamTransferService = teamTransferService;
        this.activityEventRepository = activityEventRepository;
        this.tierPricingService = tierPricingService;
    }

    @Override
    public Void handle(SwapWithBenchCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonToGive = command.pokemonToGive().trim();
        String pokemonToTake = command.pokemonToTake().trim();

        TeamTransferService.Market market = teamTransferService.openMarket(leagueId, TeamOperation.SWAP);
        DraftEntity draft = market.draft();
        LeagueEntity league = market.league();
        LeagueMember member = teamTransferService.requireMember(league, username);

        DraftPick givenPick = draft.getPicks().stream()
                .filter(p -> username.equals(p.getUsername()) && pokemonToGive.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + pokemonToGive + "' is not in your team for this league"));

        ClosedListEntity benchEntry = closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(pokemonToTake, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonToTake + "' is not in the pool for this league"));

        teamTransferService.requireOnBench(draft, pokemonToTake);

        // Tier parity check + net coin change
        ClosedListEntity giveEntry = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pokemonToGive, leagueId)
                .orElse(null); // null → treat as tier D (rank 4, price 0)

        Tier giveTier = giveEntry != null ? giveEntry.getTier() : null;
        if (tierRank(giveTier) > tierRank(benchEntry.getTier())) {
            throw new IllegalStateException(
                    "No puedes intercambiar un pokémon de tier " + giveEntry.getTier() +
                    " por uno de tier " + benchEntry.getTier() +
                    ". Debes entregar un pokémon de igual o mejor tier.");
        }

        LeagueSettings settings = league.getSettings();
        int priceGive = tierPricingService.priceForTier(settings, giveTier);
        int priceTake = tierPricingService.priceForTier(settings, benchEntry.getTier());
        int net = priceGive - priceTake; // positive = player receives coins; negative = player pays

        if (net < 0) {
            teamTransferService.charge(member, -net);
        } else {
            member.setCoinBalance(member.getCoinBalance() + net);
        }
        if (net != 0) {
            leagueRepository.save(league);
        }

        teamTransferService.replaceWithBench(draft, givenPick, benchEntry);
        draftRepository.save(draft);

        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.BENCH_SWAP)
                .actorUsername(username)
                .pokemonName(pokemonToGive)
                .pokemonName2(pokemonToTake)
                .coinsAmount(net != 0 ? net : null)
                .createdAt(Instant.now())
                .build());

        return null;
    }

    private int tierRank(Tier tier) {
        if (tier == null) return 4;
        return switch (tier) { case S -> 0; case A -> 1; case B -> 2; case C -> 3; case D -> 4; };
    }

    @Override
    public Class<SwapWithBenchCommand> commandType() {
        return SwapWithBenchCommand.class;
    }
}
