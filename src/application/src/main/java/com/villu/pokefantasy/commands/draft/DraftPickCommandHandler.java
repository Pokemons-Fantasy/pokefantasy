package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.schedule.RoundRobinScheduler;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Pokemons;
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
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DraftPickCommandHandler implements CommandHandler<DraftPickCommand, Void> {

    private static final int DEFAULT_MAX_TEAM_SIZE = 10;

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final UserRepository userRepository;
    private final LeagueRepository leagueRepository;
    private final ScheduleRepository scheduleRepository;

    public DraftPickCommandHandler(DraftRepository draftRepository,
                                   ClosedListRepository closedListRepository,
                                   UserRepository userRepository,
                                   LeagueRepository leagueRepository,
                                   ScheduleRepository scheduleRepository) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.userRepository = userRepository;
        this.leagueRepository = leagueRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    public Void handle(DraftPickCommand command) {
        if (command == null || command.username() == null || command.pokemonName() == null
                || command.username().isBlank() || command.pokemonName().isBlank()) {
            throw new IllegalArgumentException("Username and pokemonName are required");
        }

        String username = command.username().trim();
        String pokemonName = command.pokemonName().trim();
        String leagueId = command.leagueId();

        DraftEntity draft = draftRepository.findActiveByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No active draft found for league: " + leagueId));

        if (draft.getStatus() != DraftStatus.IN_PROGRESS) {
            throw new IllegalStateException("Draft is not in progress");
        }

        String currentTurn = draft.getTurnOrder().get(draft.getCurrentTurnIndex());
        if (!currentTurn.equals(username)) {
            throw new IllegalStateException("It's not your turn. Current turn: " + currentTurn);
        }

        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        // Read maxTeamSize from league settings (falls back to DEFAULT_MAX_TEAM_SIZE for existing leagues)
        LeagueEntity league = leagueRepository.findById(leagueId).orElse(null);
        int maxTeamSize = resolveMaxTeamSize(league);

        long picksInDraft = draft.getPicks().stream()
                .filter(p -> username.equals(p.getUsername()))
                .count();
        if (picksInDraft >= maxTeamSize) {
            throw new IllegalStateException("User already has the maximum of " + maxTeamSize + " Pokémon in this league");
        }

        List<Pokemons> currentPokemons = user.getPokemons() != null ? user.getPokemons() : new ArrayList<>();

        if (draft.getPicks() == null) {
            draft.setPicks(new ArrayList<>());
        }

        boolean alreadyPicked = draft.getPicks().stream()
                .anyMatch(p -> p.getPokemonName().equalsIgnoreCase(pokemonName));
        if (alreadyPicked) {
            throw new IllegalArgumentException("Pokémon '" + pokemonName + "' has already been picked");
        }

        ClosedListEntity entry = closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("Pokémon '" + pokemonName + "' is not in the closed list for this league"));

        Pokemons pokemon = new Pokemons();
        pokemon.setId(entry.getPokemonId());
        pokemon.setName(entry.getPokemonName());
        pokemon.setStats(entry.getStats());
        pokemon.setTypes(entry.getTypes());
        pokemon.setLeagueId(leagueId);

        currentPokemons.add(pokemon);
        user.setPokemons(currentPokemons);
        userRepository.updateUserWithPokemons(user);

        DraftPick pick = new DraftPick(username, entry.getPokemonName(),
                entry.getPokemonId(), draft.getCurrentRound(), Instant.now());
        draft.getPicks().add(pick);

        advanceTurn(draft, maxTeamSize);
        try {
            draftRepository.save(draft);
        } catch (OptimisticLockingFailureException exception) {
            removeAddedPokemon(user, currentPokemons, pokemon, leagueId);
            throw new IllegalStateException("Another player made a pick at the same time. Please try your pick again.", exception);
        } catch (RuntimeException exception) {
            removeAddedPokemon(user, currentPokemons, pokemon, leagueId);
            throw exception;
        }

        // When this pick completes the draft:
        // 1. Lazily initialize league settings with defaults.
        // 2. Generate the round-robin match schedule (primera + segunda vuelta).
        if (draft.getStatus() == DraftStatus.COMPLETED) {
            initLeagueSettingsIfNeeded(league);
            generateLeagueSchedule(leagueId, draft.getTurnOrder());
        }
        return null;
    }

    private void generateLeagueSchedule(String leagueId, List<String> players) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(leagueId);
        schedule.setJornadas(RoundRobinScheduler.generate(players));
        scheduleRepository.save(schedule);
    }

    private int resolveMaxTeamSize(LeagueEntity league) {
        if (league != null && league.getSettings() != null) {
            Integer max = league.getSettings().getMaxTeamSize();
            if (max != null && max > 0) return max;
        }
        return DEFAULT_MAX_TEAM_SIZE;
    }

    private void initLeagueSettingsIfNeeded(LeagueEntity league) {
        if (league != null && league.getSettings() == null) {
            league.setSettings(LeagueSettings.defaults());
            leagueRepository.save(league);
        }
    }

    private void removeAddedPokemon(UserEntity user, List<Pokemons> currentPokemons, Pokemons pokemon, String leagueId) {
        if (currentPokemons.removeIf(p -> Objects.equals(p.getId(), pokemon.getId()) && leagueId.equals(p.getLeagueId()))) {
            user.setPokemons(currentPokemons);
            userRepository.updateUserWithPokemons(user);
        }
    }

    private void advanceTurn(DraftEntity draft, int maxRounds) {
        int nextIndex = draft.getCurrentTurnIndex() + 1;
        if (nextIndex >= draft.getTurnOrder().size()) {
            int nextRound = draft.getCurrentRound() + 1;
            if (nextRound > maxRounds) {
                draft.setStatus(DraftStatus.COMPLETED);
            } else {
                draft.setCurrentRound(nextRound);
                draft.setCurrentTurnIndex(0);
            }
        } else {
            draft.setCurrentTurnIndex(nextIndex);
        }
    }

    @Override
    public Class<DraftPickCommand> commandType() {
        return DraftPickCommand.class;
    }
}
