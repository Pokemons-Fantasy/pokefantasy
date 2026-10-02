package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DraftPickCommandHandler implements CommandHandler<DraftPickCommand, Void> {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final UserRepository userRepository;
    private final LeagueRepository leagueRepository;
    private final DraftTurnNotifier draftTurnNotifier;
    private final DraftTurnService draftTurnService;
    private final DraftCompletionService draftCompletionService;

    public DraftPickCommandHandler(DraftRepository draftRepository,
                                   ClosedListRepository closedListRepository,
                                   UserRepository userRepository,
                                   LeagueRepository leagueRepository,
                                   DraftTurnNotifier draftTurnNotifier,
                                   DraftTurnService draftTurnService,
                                   DraftCompletionService draftCompletionService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.userRepository = userRepository;
        this.leagueRepository = leagueRepository;
        this.draftTurnNotifier = draftTurnNotifier;
        this.draftTurnService = draftTurnService;
        this.draftCompletionService = draftCompletionService;
    }

    @Override
    public Void handle(DraftPickCommand command) {
        if (command == null || command.username() == null || command.pokemonName() == null
                || command.username().isBlank() || command.pokemonName().isBlank()) {
            throw new IllegalArgumentException("Indica el Pokémon.");
        }

        String username = command.username().trim();
        String pokemonName = command.pokemonName().trim();
        String leagueId = command.leagueId();

        DraftEntity draft = draftRepository.findActiveByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No hay ningún draft activo en esta liga"));

        if (draft.getStatus() != DraftStatus.IN_PROGRESS) {
            throw new IllegalStateException("El draft no está en curso");
        }

        String currentTurn = draft.getTurnOrder().get(draft.getCurrentTurnIndex());
        if (!currentTurn.equals(username)) {
            throw new IllegalStateException("No es tu turno: le toca a " + currentTurn);
        }

        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("No existe el usuario '" + username + "'");
        }

        LeagueEntity league = leagueRepository.findById(leagueId).orElse(null);
        int maxTeamSize = draftTurnService.maxTeamSize(league);

        long picksInDraft = draft.getPicks().stream()
                .filter(p -> username.equals(p.getUsername()))
                .count();
        if (picksInDraft >= maxTeamSize) {
            throw new IllegalStateException("Ya tienes el máximo de " + maxTeamSize + " Pokémon en esta liga");
        }

        if (draft.getPicks() == null) {
            draft.setPicks(new ArrayList<>());
        }

        boolean alreadyPicked = draft.getPicks().stream()
                .anyMatch(p -> p.getPokemonName().equalsIgnoreCase(pokemonName));
        if (alreadyPicked) {
            throw new IllegalArgumentException("'" + pokemonName + "' ya lo ha elegido otro jugador");
        }

        ClosedListEntity entry = closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(pokemonName, leagueId)
                .orElseThrow(() -> new IllegalArgumentException("'" + pokemonName + "' no está en el pool de esta liga"));

        int price = draftTurnService.priceOf(draft, entry);
        Integer remaining = draftTurnService.remainingBudget(draft, username);
        if (remaining != null && price > remaining) {
            throw new IllegalArgumentException("No te llega: " + entry.getPokemonName() + " cuesta " + price
                    + " y te quedan " + remaining);
        }
        Integer paid = draft.getConfig() == null ? null : price;

        DraftPick pick = new DraftPick(username, entry.getPokemonName(),
                entry.getPokemonId(), draft.getCurrentRound(), Instant.now(), null, null);
        pick.setPrice(paid);
        draft.getPicks().add(pick);

        // Registro inmutable del draft original: una copia que robos/swaps/trades nunca tocan.
        if (draft.getDraftHistory() == null) {
            draft.setDraftHistory(new ArrayList<>());
        }
        DraftPick historyPick = new DraftPick(pick.getUsername(), pick.getPokemonName(),
                pick.getPokemonId(), pick.getRound(), pick.getPickedAt(),
                pick.getCustomStealPrice(), pick.getLockedUntil());
        historyPick.setPrice(paid);
        draft.getDraftHistory().add(historyPick);

        // Sin presupuesto (drafts anteriores) solo cuenta el tamaño del equipo: no hace falta el pool.
        List<ClosedListEntity> available = draft.getConfig() == null ? List.of()
                : draftTurnService.available(draft, closedListRepository.findAllByLeagueId(leagueId));
        draftTurnService.advance(draft, available, maxTeamSize);
        draft.setCurrentTurnStartedAt(Instant.now());
        draftRepository.save(draft);

        if (draft.getStatus() == DraftStatus.COMPLETED) {
            draftCompletionService.complete(leagueId, league, draft);
        } else {
            draftTurnNotifier.notifyCurrentTurn(draft, league);
        }
        return null;
    }

    @Override
    public Class<DraftPickCommand> commandType() {
        return DraftPickCommand.class;
    }
}
