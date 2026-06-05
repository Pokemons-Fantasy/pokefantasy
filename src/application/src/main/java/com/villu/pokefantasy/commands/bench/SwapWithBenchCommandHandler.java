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
import org.springframework.stereotype.Service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SwapWithBenchCommandHandler implements CommandHandler<SwapWithBenchCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final JornadaWindowService jornadaWindowService;
    private final ActivityEventRepository activityEventRepository;

    public SwapWithBenchCommandHandler(DraftRepository draftRepository,
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
    public Void handle(SwapWithBenchCommand command) {
        String leagueId = command.leagueId();
        String username = command.username();
        String pokemonToGive = command.pokemonToGive().trim();
        String pokemonToTake = command.pokemonToTake().trim();

        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Swaps are only allowed after the draft is completed"));

        // Check swap window — mismo manejo del schedule que el robo y los trades.
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No schedule found for this league"));
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        if (!jornadaWindowService.isSwapWindowOpen(schedule, league.getSettings())) {
            throw new IllegalStateException(
                    "El intercambio con la banca no está permitido en este momento. " +
                    "El plazo cerró o los resultados de la jornada anterior aún no están completos.");
        }

        boolean isMember = league.getMembers().stream()
                .anyMatch(m -> username.equals(m.getUsername()));
        if (!isMember) {
            throw new IllegalStateException("User is not a member of this league");
        }

        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        List<Pokemons> currentPokemons = user.getPokemons() != null ? new ArrayList<>(user.getPokemons()) : new ArrayList<>();

        Pokemons toGive = currentPokemons.stream()
                .filter(p -> leagueId.equals(p.getLeagueId()) && pokemonToGive.equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonToGive + "' is not in your team for this league"));

        ClosedListEntity benchEntry = closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(pokemonToTake, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonToTake + "' is not in the pool for this league"));

        Set<String> ownedNames = league.getMembers().stream()
                .map(member -> userRepository.findByUsername(member.getUsername()))
                .filter(u -> u != null && u.getPokemons() != null)
                .flatMap(u -> u.getPokemons().stream())
                .filter(p -> leagueId.equals(p.getLeagueId()))
                .map(p -> p.getName().toLowerCase())
                .collect(Collectors.toSet());

        if (ownedNames.contains(pokemonToTake.toLowerCase())) {
            throw new IllegalStateException("'" + pokemonToTake + "' is not available on the bench");
        }

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
        int priceGive = priceForTier(settings, giveTier);
        int priceTake = priceForTier(settings, benchEntry.getTier());
        int net = priceGive - priceTake; // positive = player receives coins; negative = player pays

        LeagueMember member = getMember(league, username);
        if (net < 0 && member.getCoinBalance() < -net) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas " + (-net) +
                    " pero tienes " + member.getCoinBalance() + ".");
        }
        if (net != 0) {
            member.setCoinBalance(member.getCoinBalance() + net);
            leagueRepository.save(league);
        }

        currentPokemons.remove(toGive);

        Pokemons newPokemon = new Pokemons();
        newPokemon.setId(benchEntry.getPokemonId());
        newPokemon.setName(benchEntry.getPokemonName());
        newPokemon.setStats(benchEntry.getStats());
        newPokemon.setTypes(benchEntry.getTypes());
        newPokemon.setLeagueId(leagueId);

        currentPokemons.add(newPokemon);
        user.setPokemons(currentPokemons);
        userRepository.updateUserWithPokemons(user);

        // Replace the pick in the draft so TeamsPage reflects the swap
        List<DraftPick> picks = draft.getPicks();
        for (int i = 0; i < picks.size(); i++) {
            DraftPick pick = picks.get(i);
            if (username.equals(pick.getUsername()) && pokemonToGive.equalsIgnoreCase(pick.getPokemonName())) {
                DraftPick newPick = new DraftPick(username, benchEntry.getPokemonName(),
                        benchEntry.getPokemonId(), pick.getRound(), pick.getPickedAt(), null, null);
                picks.set(i, newPick);
                break;
            }
        }
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
    public Class<SwapWithBenchCommand> commandType() {
        return SwapWithBenchCommand.class;
    }
}
