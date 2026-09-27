package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueMemberService;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.team.TeamOperation;
import com.villu.pokefantasy.team.TeamTransferService;
import lombok.extern.slf4j.Slf4j;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class StealPokemonCommandHandler implements CommandHandler<StealPokemonCommand, String> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final TeamTransferService teamTransferService;
    private final ActivityEventRepository activityEventRepository;
    private final PushNotificationPort pushNotificationPort;
    private final LeagueMemberService leagueMemberService;
    private final TierPricingService tierPricingService;

    public StealPokemonCommandHandler(DraftRepository draftRepository,
                                      ClosedListRepository closedListRepository,
                                      LeagueRepository leagueRepository,
                                      UserRepository userRepository,
                                      TeamTransferService teamTransferService,
                                      ActivityEventRepository activityEventRepository,
                                      PushNotificationPort pushNotificationPort,
                                      LeagueMemberService leagueMemberService,
                                      TierPricingService tierPricingService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.teamTransferService = teamTransferService;
        this.activityEventRepository = activityEventRepository;
        this.pushNotificationPort = pushNotificationPort;
        this.leagueMemberService = leagueMemberService;
        this.tierPricingService = tierPricingService;
    }

    @Override
    public String handle(StealPokemonCommand command) {
        String leagueId = command.leagueId();
        String stealer = command.stealer();
        String targetName = command.targetPokemonName().trim();

        TeamTransferService.Market market = teamTransferService.openMarket(leagueId, TeamOperation.STEAL);
        DraftEntity draft = market.draft();
        LeagueEntity league = market.league();

        // Find the target pick — must belong to someone other than the stealer
        DraftPick targetPick = draft.getPicks().stream()
                .filter(p -> targetName.equalsIgnoreCase(p.getPokemonName()) && !stealer.equals(p.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + targetName + "' no está en el equipo de ningún rival"));

        String victim = targetPick.getUsername();

        teamTransferService.requireUnlocked(targetPick);

        // Compute steal price
        LeagueSettings settings = league.getSettings();
        int stealPrice;
        if (targetPick.getCustomStealPrice() != null) {
            stealPrice = targetPick.getCustomStealPrice();
        } else {
            ClosedListEntity entry = closedListRepository
                    .findByPokemonNameIgnoreCaseAndLeagueId(targetName, leagueId)
                    .orElse(null);
            Tier tier = entry != null ? entry.getTier() : null;
            stealPrice = tierPricingService.priceForTier(settings, tier);
        }

        // Stealer pays, victim receives 2×
        LeagueMember stealerMember = teamTransferService.requireMember(league, stealer);
        teamTransferService.charge(stealerMember, stealPrice);
        LeagueMember victimMember = leagueMemberService.requireMember(league, victim);
        victimMember.setCoinBalance(victimMember.getCoinBalance() + stealPrice * 2);
        leagueRepository.save(league);

        teamTransferService.transfer(targetPick, stealer, Instant.now());
        draftRepository.save(draft);

        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.STEAL)
                .actorUsername(stealer)
                .targetUsername(victim)
                .pokemonName(targetName)
                .coinsAmount(stealPrice)
                .createdAt(Instant.now())
                .build());

        UserEntity victimUser = userRepository.findByUsername(victim);
        if (victimUser != null && !victimUser.getFcmTokens().isEmpty()) {
            pushNotificationPort.send(
                    victimUser.getFcmTokens(),
                    "Te han robado un Pokémon",
                    stealer + " te ha robado a " + targetName);
        }

        return victim;
    }

    @Override
    public Class<StealPokemonCommand> commandType() {
        return StealPokemonCommand.class;
    }
}
