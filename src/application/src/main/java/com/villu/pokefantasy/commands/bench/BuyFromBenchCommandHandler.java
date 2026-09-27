package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueSettings;
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
public class BuyFromBenchCommandHandler implements CommandHandler<BuyFromBenchCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final TeamTransferService teamTransferService;
    private final ActivityEventRepository activityEventRepository;
    private final TierPricingService tierPricingService;

    public BuyFromBenchCommandHandler(DraftRepository draftRepository,
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
    public Void handle(BuyFromBenchCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonName = command.pokemonName().trim();

        TeamTransferService.Market market = teamTransferService.openMarket(leagueId, TeamOperation.BUY);
        DraftEntity draft = market.draft();
        LeagueEntity league = market.league();
        LeagueMember buyerMember = teamTransferService.requireMember(league, username);

        // Pokemon must exist in the pool
        ClosedListEntity benchEntry = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonName + "' is not in the pool for this league"));

        teamTransferService.requireOnBench(draft, pokemonName);

        // Buyer's team must not exceed maxTeamSize
        LeagueSettings settings = league.getSettings();
        int maxTeamSize = (settings != null && settings.getMaxTeamSize() != null) ? settings.getMaxTeamSize() : 20;

        long currentTeamSize = draft.teamSize(username);

        if (currentTeamSize >= maxTeamSize) {
            throw new IllegalStateException(
                    "Tu equipo está lleno (" + maxTeamSize + "/" + maxTeamSize +
                    "). No puedes comprar más pokémon de la banca.");
        }

        int price = tierPricingService.priceForTier(settings, benchEntry.getTier());
        teamTransferService.charge(buyerMember, price);
        leagueRepository.save(league);

        teamTransferService.addFromBench(draft, username, benchEntry, Instant.now());
        draftRepository.save(draft);

        // Log activity event
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.BENCH_PURCHASE)
                .actorUsername(username)
                .pokemonName(benchEntry.getPokemonName())
                .coinsAmount(price)
                .createdAt(Instant.now())
                .build());

        return null;
    }

    @Override
    public Class<BuyFromBenchCommand> commandType() {
        return BuyFromBenchCommand.class;
    }
}
