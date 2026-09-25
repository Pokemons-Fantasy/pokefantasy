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
public class ReleasePokemonCommandHandler implements CommandHandler<ReleasePokemonCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final TeamTransferService teamTransferService;
    private final ActivityEventRepository activityEventRepository;
    private final TierPricingService tierPricingService;

    public ReleasePokemonCommandHandler(DraftRepository draftRepository,
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
    public Void handle(ReleasePokemonCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonName = command.pokemonName().trim();

        TeamTransferService.Market market = teamTransferService.openMarket(leagueId, TeamOperation.RELEASE);
        DraftEntity draft = market.draft();
        LeagueEntity league = market.league();

        DraftPick pick = draft.getPicks().stream()
                .filter(p -> username.equals(p.getUsername())
                          && pokemonName.equalsIgnoreCase(p.getPokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tienes a '" + pokemonName + "' en tu equipo"));
        teamTransferService.requireUnlocked(pick);

        // Recompensa: la mitad del precio de su tier (la tier está en la closed list, no en el pick)
        LeagueSettings settings = league.getSettings();

        ClosedListEntity entry = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                .orElse(null);
        Tier tier = entry != null ? entry.getTier() : null;
        int reward = tierPricingService.priceForTier(settings, tier) / 2;

        // --- Execute ---

        teamTransferService.releaseToBench(draft, pick);
        draftRepository.save(draft);

        // Add coins to member (reward = 0 if tier unknown, safe fallback)
        LeagueMember member = teamTransferService.requireMember(league, username);
        member.setCoinBalance(member.getCoinBalance() + reward);
        leagueRepository.save(league);

        // Activity event
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.POKEMON_RELEASED)
                .actorUsername(username)
                .pokemonName(pokemonName)
                .coinsAmount(reward)
                .createdAt(Instant.now())
                .build());

        return null;
    }

    @Override
    public Class<ReleasePokemonCommand> commandType() {
        return ReleasePokemonCommand.class;
    }
}
