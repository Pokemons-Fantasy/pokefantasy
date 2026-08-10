package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BuyFromBenchCommandHandler implements CommandHandler<BuyFromBenchCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final JornadaWindowService jornadaWindowService;
    private final ActivityEventRepository activityEventRepository;

    public BuyFromBenchCommandHandler(DraftRepository draftRepository,
                                      ClosedListRepository closedListRepository,
                                      LeagueRepository leagueRepository,
                                      UserRepository userRepository,
                                      ScheduleRepository scheduleRepository,
                                      JornadaWindowService jornadaWindowService,
                                      ActivityEventRepository activityEventRepository) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.jornadaWindowService = jornadaWindowService;
        this.activityEventRepository = activityEventRepository;
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
            throw new IllegalArgumentException("User is not a member of this league");
        }

        // 4. Pokemon must exist in the pool
        ClosedListEntity benchEntry = closedListRepository
                .findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonName + "' is not in the pool for this league"));

        // 5. Pokemon must actually be on the bench (not already owned by anyone)
        Set<String> ownedNames = league.getMembers().stream()
                .map(member -> userRepository.findByUsername(member.getUsername()))
                .filter(u -> u != null && u.getPokemons() != null)
                .flatMap(u -> u.getPokemons().stream())
                .filter(p -> leagueId.equals(p.getLeagueId()))
                .map(p -> p.getName().toLowerCase())
                .collect(Collectors.toSet());

        if (ownedNames.contains(pokemonName.toLowerCase())) {
            throw new IllegalStateException("'" + pokemonName + "' is not available on the bench");
        }

        // 6. Buyer's team must not exceed maxTeamSize
        UserEntity buyer = userRepository.findByUsername(username);
        if (buyer == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        LeagueSettings settings = league.getSettings();
        int maxTeamSize = (settings != null && settings.getMaxTeamSize() != null) ? settings.getMaxTeamSize() : 20;

        long currentTeamSize = buyer.getPokemons() == null ? 0 :
                buyer.getPokemons().stream().filter(p -> leagueId.equals(p.getLeagueId())).count();

        if (currentTeamSize >= maxTeamSize) {
            throw new IllegalStateException(
                    "Tu equipo está lleno (" + maxTeamSize + "/" + maxTeamSize +
                    "). No puedes comprar más pokémon de la banca.");
        }

        // 7. Buyer must have enough coins
        int price = priceForTier(settings, benchEntry.getTier());
        LeagueMember buyerMember = getMember(league, username);

        if (buyerMember.getCoinBalance() < price) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas " + price +
                    " pero tienes " + buyerMember.getCoinBalance() + ".");
        }

        // --- Perform updates ---

        // Deduct coins
        buyerMember.setCoinBalance(buyerMember.getCoinBalance() - price);
        leagueRepository.save(league);

        // Add pokemon to buyer's team
        List<Pokemons> currentPokemons = buyer.getPokemons() != null ?
                new ArrayList<>(buyer.getPokemons()) : new ArrayList<>();

        Pokemons newPokemon = new Pokemons();
        newPokemon.setId(benchEntry.getPokemonId());
        newPokemon.setName(benchEntry.getPokemonName());
        newPokemon.setStats(benchEntry.getStats());
        newPokemon.setTypes(benchEntry.getTypes());
        newPokemon.setLeagueId(leagueId);

        currentPokemons.add(newPokemon);
        buyer.setPokemons(currentPokemons);
        userRepository.updateUserWithPokemons(buyer);

        // Add DraftPick so TeamsPage reflects the purchase. round=0 is the sentinel
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

        try {
            draftRepository.save(draft);
        } catch (OptimisticLockingFailureException exception) {
            compensateBuy(league, buyerMember, price, buyer, newPokemon, leagueId);
            throw new IllegalStateException("Otro jugador modificó el draft al mismo tiempo. Inténtalo de nuevo.", exception);
        } catch (RuntimeException exception) {
            compensateBuy(league, buyerMember, price, buyer, newPokemon, leagueId);
            throw exception;
        }

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

    /**
     * Revierte monedas y pokémon si draftRepository.save(draft) falla — evita dejar
     * user.pokemons/coinBalance mutados sin el DraftPick correspondiente actualizado.
     */
    private void compensateBuy(LeagueEntity league, LeagueMember buyerMember, int price,
                               UserEntity buyer, Pokemons newPokemon, String leagueId) {
        buyerMember.setCoinBalance(buyerMember.getCoinBalance() + price);
        leagueRepository.save(league);

        List<Pokemons> pokemons = new ArrayList<>(buyer.getPokemons());
        pokemons.removeIf(p -> leagueId.equals(p.getLeagueId()) && newPokemon.getName().equalsIgnoreCase(p.getName()));
        buyer.setPokemons(pokemons);
        userRepository.updateUserWithPokemons(buyer);
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
    public Class<BuyFromBenchCommand> commandType() {
        return BuyFromBenchCommand.class;
    }
}
