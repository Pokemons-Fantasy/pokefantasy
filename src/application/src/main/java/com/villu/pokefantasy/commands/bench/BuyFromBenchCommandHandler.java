package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMemberService;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BuyFromBenchCommandHandler implements CommandHandler<BuyFromBenchCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final ScheduleRepository scheduleRepository;
    private final JornadaWindowService jornadaWindowService;
    private final ActivityEventRepository activityEventRepository;
    private final LeagueMemberService leagueMemberService;
    private final TierPricingService tierPricingService;

    public BuyFromBenchCommandHandler(DraftRepository draftRepository,
                                      ClosedListRepository closedListRepository,
                                      LeagueRepository leagueRepository,
                                      ScheduleRepository scheduleRepository,
                                      JornadaWindowService jornadaWindowService,
                                      ActivityEventRepository activityEventRepository,
                                      LeagueMemberService leagueMemberService,
                                      TierPricingService tierPricingService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.scheduleRepository = scheduleRepository;
        this.jornadaWindowService = jornadaWindowService;
        this.activityEventRepository = activityEventRepository;
        this.leagueMemberService = leagueMemberService;
        this.tierPricingService = tierPricingService;
    }

    @Override
    public Void handle(BuyFromBenchCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonName = command.pokemonName().trim();

        // 1. Draft must be completed
        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Bench purchases are only allowed after the draft is completed"));

        // 2. Swap window must be open (buy uses the same window as swap)
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No schedule found for this league"));
        // 3. User must be a league member
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        if (!jornadaWindowService.isSwapWindowOpen(schedule, league.getSettings())) {
            throw new IllegalStateException(
                    "La compra de pokémon de la banca no está permitida en este momento. " +
                    "El plazo cerró o los resultados de la jornada anterior aún no están completos.");
        }

        boolean isMember = league.getMembers().stream()
                .anyMatch(m -> username.equals(m.getUsername()));
        if (!isMember) {
            throw new ForbiddenOperationException("User '" + username + "' is not a member of league: " + leagueId);
        }

        // 4. Pokemon must exist in the pool
        ClosedListEntity benchEntry = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonName + "' is not in the pool for this league"));

        // 5. Pokemon must actually be on the bench (not already owned by anyone)
        if (draft.ownedPokemonNames().contains(pokemonName.toLowerCase())) {
            throw new IllegalStateException("'" + pokemonName + "' is not available on the bench");
        }

        // 6. Buyer's team must not exceed maxTeamSize
        LeagueSettings settings = league.getSettings();
        int maxTeamSize = (settings != null && settings.getMaxTeamSize() != null) ? settings.getMaxTeamSize() : 20;

        long currentTeamSize = draft.teamSize(username);

        if (currentTeamSize >= maxTeamSize) {
            throw new IllegalStateException(
                    "Tu equipo está lleno (" + maxTeamSize + "/" + maxTeamSize +
                    "). No puedes comprar más pokémon de la banca.");
        }

        // 7. Buyer must have enough coins
        int price = tierPricingService.priceForTier(settings, benchEntry.getTier());
        LeagueMember buyerMember = leagueMemberService.requireMember(league, username);

        if (buyerMember.getCoinBalance() < price) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas " + price +
                    " pero tienes " + buyerMember.getCoinBalance() + ".");
        }

        // --- Perform updates ---

        // Deduct coins
        buyerMember.setCoinBalance(buyerMember.getCoinBalance() - price);
        leagueRepository.save(league);

        // Add pokemon to buyer's team (DraftPick). round=0 is the sentinel
        // for "bought from bench" (draft rounds start at 1).
        DraftPick newPick = new DraftPick(
                username,
                benchEntry.getPokemonName(),
                benchEntry.getPokemonId(),
                0,
                Instant.now(),
                null,
                null
        );
        draft.getPicks().add(newPick);

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
