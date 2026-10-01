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
                        "'" + pokemonToGive + "' no está en tu equipo en esta liga"));
        // Como al liberarlo: un Pokémon recién robado o intercambiado no vuelve a la banca hasta que se desbloquea
        teamTransferService.requireUnlocked(givenPick);

        ClosedListEntity benchEntry = closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(pokemonToTake, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonToTake + "' no está en el pool de esta liga"));

        teamTransferService.requireOnBench(draft, pokemonToTake);

        // Diferencia de precio de mercado entre los dos tiers: bajar de tier la cobra el jugador; subir la paga
        // (409 si no le llega). Equivale a liberar uno y comprar el otro, en un paso y sin hueco libre.
        ClosedListEntity giveEntry = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pokemonToGive, leagueId)
                .orElse(null); // sin entrada en el pool: precio 0

        Tier giveTier = giveEntry != null ? giveEntry.getTier() : null;
        LeagueSettings settings = league.getSettings();
        int priceGive = tierPricingService.priceForTier(settings, giveTier);
        int priceTake = tierPricingService.priceForTier(settings, benchEntry.getTier());
        int net = priceGive - priceTake; // positivo: recibe monedas; negativo: paga

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

    @Override
    public Class<SwapWithBenchCommand> commandType() {
        return SwapWithBenchCommand.class;
    }
}
