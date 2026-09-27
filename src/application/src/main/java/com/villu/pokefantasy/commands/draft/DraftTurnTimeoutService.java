package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turno vencido del draft → pick aleatorio en nombre del jugador. Lo usan el auto-pick que lanza el
 * cliente al llegar a cero ({@link AutoPickDraftCommandHandler}) y el job del servidor que cubre el caso
 * de que nadie tenga la app abierta ({@link ExpireDraftTurnCommandHandler}).
 */
@Service
public class DraftTurnTimeoutService {

    private final DraftRepository draftRepository;
    private final ClosedListRepository closedListRepository;
    private final DraftPickCommandHandler draftPickCommandHandler;
    private final Random random = new Random();

    public DraftTurnTimeoutService(DraftRepository draftRepository,
                                   ClosedListRepository closedListRepository,
                                   DraftPickCommandHandler draftPickCommandHandler) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.draftPickCommandHandler = draftPickCommandHandler;
    }

    /** Fin del turno actual, o vacío si la liga no tiene temporizador o no consta cuándo empezó. */
    static Optional<Instant> turnDeadline(DraftEntity draft, LeagueEntity league) {
        LeagueSettings settings = league.getSettings() != null ? league.getSettings() : LeagueSettings.defaults();
        Integer timer = settings.getTurnTimerSeconds();
        if (timer == null || timer <= 0 || draft.getCurrentTurnStartedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(draft.getCurrentTurnStartedAt().plusSeconds(timer));
    }

    /**
     * Hace el pick aleatorio del jugador en turno. Lanza {@link IllegalStateException} si no hay draft en
     * curso, no hay temporizador o el turno aún no ha vencido (p. ej. otro cliente se adelantó).
     */
    public void autoPickExpiredTurn(LeagueEntity league) throws Exception {
        String leagueId = league.getId();
        DraftEntity draft = draftRepository.findActiveByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No active draft found for league: " + leagueId));

        if (draft.getStatus() != DraftStatus.IN_PROGRESS) {
            throw new IllegalStateException("Draft is not in progress");
        }

        LeagueSettings settings = league.getSettings() != null ? league.getSettings() : LeagueSettings.defaults();
        Integer timer = settings.getTurnTimerSeconds();
        if (timer == null || timer <= 0) {
            throw new IllegalStateException("Turn timer is not enabled for this league");
        }

        if (draft.getCurrentTurnStartedAt() == null) {
            throw new IllegalStateException("Turn start time not recorded");
        }

        Instant deadline = draft.getCurrentTurnStartedAt().plusSeconds(timer);
        if (Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("Turn timer has not expired yet");
        }

        String currentPlayer = draft.getTurnOrder().get(draft.getCurrentTurnIndex());

        Set<String> pickedNames = draft.getPicks().stream()
                .map(DraftPick::getPokemonName)
                .collect(Collectors.toSet());

        List<ClosedListEntity> available = closedListRepository.findAllByLeagueId(leagueId)
                .stream()
                .filter(p -> !pickedNames.contains(p.getPokemonName()))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            throw new IllegalStateException("No Pokémon available for auto-pick");
        }

        String randomPokemon = available.get(random.nextInt(available.size())).getPokemonName();

        // Delegate to the standard pick flow (validates, records, advances turn, generates schedule if completed)
        draftPickCommandHandler.handle(new DraftPickCommand(currentPlayer, randomPokemon, leagueId));
    }
}
