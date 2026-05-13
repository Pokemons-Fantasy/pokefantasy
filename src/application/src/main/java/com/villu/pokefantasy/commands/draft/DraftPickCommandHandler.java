package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DraftPickCommandHandler implements CommandHandler<DraftPickCommand, Void> {

    private static final int MAX_POKEMONS_PER_USER = 10;

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final UserRepository userRepository;

    public DraftPickCommandHandler(DraftRepository draftRepository,
                                   ClosedListRepository closedListRepository,
                                   UserRepository userRepository) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Void handle(DraftPickCommand command) {
        if (command == null || command.username() == null || command.pokemonName() == null
                || command.username().isBlank() || command.pokemonName().isBlank()) {
            throw new IllegalArgumentException("Username and pokemonName are required");
        }

        String username = command.username().trim();
        String pokemonName = command.pokemonName().trim();
        DraftEntity draft = draftRepository.findActive()
                .orElseThrow(() -> new IllegalStateException("No active draft found"));

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

        List<Pokemons> currentPokemons = user.getPokemons() != null ? user.getPokemons() : new ArrayList<>();
        if (currentPokemons.size() >= MAX_POKEMONS_PER_USER) {
            throw new IllegalStateException("User already has the maximum of " + MAX_POKEMONS_PER_USER + " Pokémon");
        }

        if (draft.getPicks() == null) {
            draft.setPicks(new ArrayList<>());
        }

        boolean alreadyPicked = draft.getPicks().stream()
                .anyMatch(p -> p.getPokemonName().equalsIgnoreCase(pokemonName));
        if (alreadyPicked) {
            throw new IllegalArgumentException("Pokémon '" + pokemonName + "' has already been picked");
        }

        ClosedListEntity entry = closedListRepository.findByPokemonNameIgnoreCase(pokemonName)
                .orElseThrow(() -> new IllegalArgumentException("Pokémon '" + pokemonName + "' is not in the closed list"));

        Pokemons pokemon = new Pokemons();
        pokemon.setId(entry.getPokemonId());
        pokemon.setName(entry.getPokemonName());
        pokemon.setStats(entry.getStats());
        pokemon.setTypes(entry.getTypes());

        currentPokemons.add(pokemon);
        user.setPokemons(currentPokemons);
        userRepository.updateUserWithPokemons(user);

        DraftPick pick = new DraftPick(username, entry.getPokemonName(),
                entry.getPokemonId(), draft.getCurrentRound(), Instant.now());
        draft.getPicks().add(pick);

        advanceTurn(draft);
        try {
            draftRepository.save(draft);
        } catch (RuntimeException exception) {
            rollbackUserPokemon(user, currentPokemons, pokemon);
            if (exception instanceof OptimisticLockingFailureException) {
                throw new IllegalStateException("Draft changed while processing the pick. Please retry.", exception);
            }
            throw exception;
        }
        return null;
    }

    private void rollbackUserPokemon(UserEntity user, List<Pokemons> currentPokemons, Pokemons pokemon) {
        for (int i = currentPokemons.size() - 1; i >= 0; i--) {
            if (Objects.equals(currentPokemons.get(i).getId(), pokemon.getId())) {
                currentPokemons.remove(i);
                user.setPokemons(currentPokemons);
                userRepository.updateUserWithPokemons(user);
                return;
            }
        }
    }

    private void advanceTurn(DraftEntity draft) {
        int nextIndex = draft.getCurrentTurnIndex() + 1;
        if (nextIndex >= draft.getTurnOrder().size()) {
            int nextRound = draft.getCurrentRound() + 1;
            if (nextRound > MAX_POKEMONS_PER_USER) {
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
