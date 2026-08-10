package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.league.LeagueMemberService;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.mediator.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class StealPokemonCommandHandler implements CommandHandler<StealPokemonCommand, String> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final JornadaWindowService jornadaWindowService;
    private final ActivityEventRepository activityEventRepository;
    private final PushNotificationPort pushNotificationPort;
    private final LeagueMemberService leagueMemberService;
    private final TierPricingService tierPricingService;

    public StealPokemonCommandHandler(DraftRepository draftRepository,
                                      ClosedListRepository closedListRepository,
                                      LeagueRepository leagueRepository,
                                      UserRepository userRepository,
                                      ScheduleRepository scheduleRepository,
                                      JornadaWindowService jornadaWindowService,
                                      ActivityEventRepository activityEventRepository,
                                      PushNotificationPort pushNotificationPort,
                                      LeagueMemberService leagueMemberService,
                                      TierPricingService tierPricingService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.jornadaWindowService = jornadaWindowService;
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

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Steals are only allowed after the draft is completed"));

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No schedule found for this league"));

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        if (!jornadaWindowService.isStealWindowOpen(schedule, league.getSettings())) {
            throw new IllegalStateException(
                    "La ventana de robos no está abierta.");
        }

        // Find the target pick — must belong to someone other than the stealer
        DraftPick targetPick = draft.getPicks().stream()
                .filter(p -> targetName.equalsIgnoreCase(p.getPokemonName()) && !stealer.equals(p.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + targetName + "' no está en el equipo de ningún rival"));

        String victim = targetPick.getUsername();

        // Lock check: bloqueado 7 días desde el robo/trade
        if (targetPick.getLockedUntil() != null && Instant.now().isBefore(targetPick.getLockedUntil())) {
            throw new IllegalStateException(
                    "'" + targetName + "' está bloqueado hasta " + targetPick.getLockedUntil() + ".");
        }

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

        // Validate stealer balance
        LeagueMember stealerMember = leagueMemberService.requireMember(league, stealer);
        if (stealerMember.getCoinBalance() < stealPrice) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas " + stealPrice +
                    " pero tienes " + stealerMember.getCoinBalance() + ".");
        }

        // Transfer coins: stealer pays, victim receives 2×
        stealerMember.setCoinBalance(stealerMember.getCoinBalance() - stealPrice);
        LeagueMember victimMember = leagueMemberService.requireMember(league, victim);
        victimMember.setCoinBalance(victimMember.getCoinBalance() + stealPrice * 2);
        leagueRepository.save(league);

        // Transfer pokemon in user documents
        UserEntity victimUser = userRepository.findByUsername(victim);
        Pokemons removedFromVictim = null;
        if (victimUser != null && victimUser.getPokemons() != null) {
            List<Pokemons> vPokemons = new ArrayList<>(victimUser.getPokemons());
            removedFromVictim = vPokemons.stream()
                    .filter(p -> leagueId.equals(p.getLeagueId()) && targetName.equalsIgnoreCase(p.getName()))
                    .findFirst().orElse(null);
            vPokemons.removeIf(p -> leagueId.equals(p.getLeagueId()) && targetName.equalsIgnoreCase(p.getName()));
            victimUser.setPokemons(vPokemons);
            userRepository.updateUserWithPokemons(victimUser);
        }

        UserEntity stealerUser = userRepository.findByUsername(stealer);
        Pokemons stolen = null;
        if (stealerUser != null) {
            List<Pokemons> sPokemons = stealerUser.getPokemons() != null
                    ? new ArrayList<>(stealerUser.getPokemons()) : new ArrayList<>();
            // Build pokemon entry from closedList or existing pick data
            ClosedListEntity entry = closedListRepository
                    .findByPokemonNameIgnoreCaseAndLeagueId(targetName, leagueId)
                    .orElse(null);
            stolen = new Pokemons();
            stolen.setId(targetPick.getPokemonId());
            stolen.setName(targetPick.getPokemonName());
            stolen.setLeagueId(leagueId);
            if (entry != null) {
                stolen.setStats(entry.getStats());
                stolen.setTypes(entry.getTypes());
            }
            sPokemons.add(stolen);
            stealerUser.setPokemons(sPokemons);
            userRepository.updateUserWithPokemons(stealerUser);
        }

        // Transfer pick in draft: update username + set lock + preserve customStealPrice
        targetPick.setUsername(stealer);
        targetPick.setLockedUntil(Instant.now().plus(7, ChronoUnit.DAYS));
        targetPick.setPickedAt(Instant.now());
        // customStealPrice is intentionally preserved (inherited by new owner)

        try {
            draftRepository.save(draft);
        } catch (OptimisticLockingFailureException exception) {
            compensateSteal(league, stealerMember, victimMember, stealPrice,
                    victimUser, removedFromVictim, stealerUser, stolen, leagueId);
            throw new IllegalStateException("Otro jugador modificó el draft al mismo tiempo. Inténtalo de nuevo.", exception);
        } catch (RuntimeException exception) {
            compensateSteal(league, stealerMember, victimMember, stealPrice,
                    victimUser, removedFromVictim, stealerUser, stolen, leagueId);
            throw exception;
        }

        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(leagueId)
                .type(ActivityEventType.STEAL)
                .actorUsername(stealer)
                .targetUsername(victim)
                .pokemonName(targetName)
                .coinsAmount(stealPrice)
                .createdAt(Instant.now())
                .build());

        if (victimUser != null && !victimUser.getFcmTokens().isEmpty()) {
            pushNotificationPort.send(
                    victimUser.getFcmTokens(),
                    "Te han robado un Pokémon",
                    stealer + " te ha robado a " + targetName);
        }

        return victim;
    }

    /**
     * Revierte monedas y pokémon si draftRepository.save(draft) falla — evita dejar
     * user.pokemons/coinBalance mutados sin el DraftPick correspondiente actualizado.
     */
    private void compensateSteal(LeagueEntity league, LeagueMember stealerMember, LeagueMember victimMember,
                                 int stealPrice, UserEntity victimUser, Pokemons removedFromVictim,
                                 UserEntity stealerUser, Pokemons stolen, String leagueId) {
        stealerMember.setCoinBalance(stealerMember.getCoinBalance() + stealPrice);
        victimMember.setCoinBalance(victimMember.getCoinBalance() - stealPrice * 2);
        leagueRepository.save(league);

        // removedFromVictim/stolen solo son no-null si victimUser/stealerUser ya lo eran
        // en el flujo principal (misma referencia, misma invocación del handler).
        if (removedFromVictim != null) {
            List<Pokemons> vPokemons = new ArrayList<>(victimUser.getPokemons());
            vPokemons.add(removedFromVictim);
            victimUser.setPokemons(vPokemons);
            userRepository.updateUserWithPokemons(victimUser);
        }
        if (stolen != null) {
            List<Pokemons> sPokemons = new ArrayList<>(stealerUser.getPokemons());
            sPokemons.removeIf(p -> leagueId.equals(p.getLeagueId()) && stolen.getName().equalsIgnoreCase(p.getName()));
            stealerUser.setPokemons(sPokemons);
            userRepository.updateUserWithPokemons(stealerUser);
        }
    }

    @Override
    public Class<StealPokemonCommand> commandType() {
        return StealPokemonCommand.class;
    }
}
