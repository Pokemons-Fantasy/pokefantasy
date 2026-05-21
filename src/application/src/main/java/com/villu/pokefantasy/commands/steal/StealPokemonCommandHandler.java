package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StealPokemonCommandHandler implements CommandHandler<StealPokemonCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final JornadaWindowService jornadaWindowService;

    public StealPokemonCommandHandler(DraftRepository draftRepository,
                                      ClosedListRepository closedListRepository,
                                      LeagueRepository leagueRepository,
                                      UserRepository userRepository,
                                      ScheduleRepository scheduleRepository,
                                      JornadaWindowService jornadaWindowService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.jornadaWindowService = jornadaWindowService;
    }

    @Override
    public Void handle(StealPokemonCommand command) {
        String leagueId = command.leagueId();
        String stealer = command.stealer();
        String targetName = command.targetPokemonName().trim();

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Steals are only allowed after the draft is completed"));

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No schedule found for this league"));

        if (!jornadaWindowService.isStealWindowOpen(schedule)) {
            throw new IllegalStateException(
                    "La ventana de robos no está abierta. El plazo cierra el jueves a las 23:59.");
        }

        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        // Find the target pick — must belong to someone other than the stealer
        DraftPick targetPick = draft.getPicks().stream()
                .filter(p -> targetName.equalsIgnoreCase(p.getPokemonName()) && !stealer.equals(p.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + targetName + "' no está en el equipo de ningún rival"));

        String victim = targetPick.getUsername();

        // Lock check: if stolen this jornada, it cannot be stolen again until it's completed
        if (targetPick.getLockedUntilRound() != null) {
            int lockedRound = targetPick.getLockedUntilRound();
            boolean stillLocked = schedule.getJornadas().stream()
                    .filter(j -> j.getRoundNumber() == lockedRound)
                    .findFirst()
                    .map(j -> j.getMatches().stream()
                            .anyMatch(m -> com.villu.pokefantasy.dto.MatchStatus.PENDING.equals(m.getStatus())))
                    .orElse(false);
            if (stillLocked) {
                throw new IllegalStateException(
                        "'" + targetName + "' está bloqueado hasta que finalice la jornada actual.");
            }
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
            stealPrice = priceForTier(settings, tier);
        }

        // Validate stealer balance
        LeagueMember stealerMember = getMember(league, stealer);
        if (stealerMember.getCoinBalance() < stealPrice) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas " + stealPrice +
                    " pero tienes " + stealerMember.getCoinBalance() + ".");
        }

        // Transfer coins: stealer pays, victim receives 2×
        stealerMember.setCoinBalance(stealerMember.getCoinBalance() - stealPrice);
        LeagueMember victimMember = getMember(league, victim);
        victimMember.setCoinBalance(victimMember.getCoinBalance() + stealPrice * 2);
        leagueRepository.save(league);

        // Transfer pokemon in user documents
        UserEntity victimUser = userRepository.findByUsername(victim);
        if (victimUser != null && victimUser.getPokemons() != null) {
            List<Pokemons> vPokemons = new ArrayList<>(victimUser.getPokemons());
            vPokemons.removeIf(p -> leagueId.equals(p.getLeagueId()) && targetName.equalsIgnoreCase(p.getName()));
            victimUser.setPokemons(vPokemons);
            userRepository.updateUserWithPokemons(victimUser);
        }

        UserEntity stealerUser = userRepository.findByUsername(stealer);
        if (stealerUser != null) {
            List<Pokemons> sPokemons = stealerUser.getPokemons() != null
                    ? new ArrayList<>(stealerUser.getPokemons()) : new ArrayList<>();
            // Build pokemon entry from closedList or existing pick data
            ClosedListEntity entry = closedListRepository
                    .findByPokemonNameIgnoreCaseAndLeagueId(targetName, leagueId)
                    .orElse(null);
            Pokemons stolen = new Pokemons();
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
        Optional<Jornada> activeJornada = jornadaWindowService.getActiveJornada(schedule);
        int lockedRound = activeJornada.map(Jornada::getRoundNumber).orElse(0);

        targetPick.setUsername(stealer);
        targetPick.setLockedUntilRound(lockedRound);
        targetPick.setPickedAt(Instant.now());
        // customStealPrice is intentionally preserved (inherited by new owner)

        draftRepository.save(draft);
        return null;
    }

    private LeagueMember getMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equals(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Member not found: " + username));
    }

    private int priceForTier(LeagueSettings settings, Tier tier) {
        if (settings == null || tier == null) return 0;
        Integer price = switch (tier) {
            case S -> settings.getPriceTierS();
            case A -> settings.getPriceTierA();
            case B -> settings.getPriceTierB();
            case C -> settings.getPriceTierC();
            case D -> settings.getPriceTierD();
        };
        return price != null ? price : 0;
    }

    @Override
    public Class<StealPokemonCommand> commandType() {
        return StealPokemonCommand.class;
    }
}
