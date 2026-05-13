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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        DraftEntity draft = draftRepository.findActive()
                .orElseThrow(() -> new IllegalStateException("No active draft found"));

        if (draft.getStatus() != DraftStatus.IN_PROGRESS) {
            throw new IllegalStateException("Draft is not in progress");
        }

        String currentTurn = draft.getTurnOrder().get(draft.getCurrentTurnIndex());
        if (!currentTurn.equals(command.username())) {
            throw new IllegalStateException("It's not your turn. Current turn: " + currentTurn);
        }

        UserEntity user = userRepository.findByUsername(command.username());
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + command.username());
        }

        List<Pokemons> currentPokemons = user.getPokemons() != null ? user.getPokemons() : new ArrayList<>();
        if (currentPokemons.size() >= MAX_POKEMONS_PER_USER) {
            throw new IllegalStateException("User already has the maximum of " + MAX_POKEMONS_PER_USER + " Pokémon");
        }

        boolean alreadyPicked = draft.getPicks().stream()
                .anyMatch(p -> p.getPokemonName().equalsIgnoreCase(command.pokemonName()));
        if (alreadyPicked) {
            throw new IllegalArgumentException("Pokémon '" + command.pokemonName() + "' has already been picked");
        }

        ClosedListEntity entry = closedListRepository.findAll().stream()
                .filter(e -> e.getPokemonName().equalsIgnoreCase(command.pokemonName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pokémon '" + command.pokemonName() + "' is not in the closed list"));

        Pokemons pokemon = new Pokemons();
        pokemon.setId(entry.getPokemonId());
        pokemon.setName(entry.getPokemonName());
        pokemon.setStats(entry.getStats());
        pokemon.setTypes(entry.getTypes());

        currentPokemons.add(pokemon);
        user.setPokemons(currentPokemons);
        userRepository.updateUserWithPokemons(user);

        DraftPick pick = new DraftPick(command.username(), entry.getPokemonName(),
                entry.getPokemonId(), draft.getCurrentRound(), Instant.now());
        draft.getPicks().add(pick);

        advanceTurn(draft);
        draftRepository.save(draft);
        return null;
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
