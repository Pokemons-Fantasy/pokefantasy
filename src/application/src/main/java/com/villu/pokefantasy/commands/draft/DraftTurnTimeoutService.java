package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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
    private final DraftTurnService draftTurnService;
    private final Random random = new Random();

    public DraftTurnTimeoutService(DraftRepository draftRepository,
                                   ClosedListRepository closedListRepository,
                                   DraftPickCommandHandler draftPickCommandHandler,
                                   DraftTurnService draftTurnService) {
        this.draftRepository = draftRepository;
        this.closedListRepository = closedListRepository;
        this.draftPickCommandHandler = draftPickCommandHandler;
        this.draftTurnService = draftTurnService;
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
                .orElseThrow(() -> new IllegalStateException("No hay ningún draft activo en esta liga"));

        if (draft.getStatus() != DraftStatus.IN_PROGRESS) {
            throw new IllegalStateException("El draft no está en curso");
        }

        LeagueSettings settings = league.getSettings() != null ? league.getSettings() : LeagueSettings.defaults();
        Integer timer = settings.getTurnTimerSeconds();
        if (timer == null || timer <= 0) {
            throw new IllegalStateException("Esta liga no tiene tiempo por turno");
        }

        if (draft.getCurrentTurnStartedAt() == null) {
            throw new IllegalStateException("No consta cuándo empezó el turno");
        }

        Instant deadline = draft.getCurrentTurnStartedAt().plusSeconds(timer);
        if (Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("El tiempo del turno aún no se ha agotado");
        }

        String currentPlayer = draft.getTurnOrder().get(draft.getCurrentTurnIndex());

        List<ClosedListEntity> available = draftTurnService.available(draft,
                closedListRepository.findAllByLeagueId(leagueId));
        Integer remaining = draftTurnService.remainingBudget(draft, currentPlayer);
        List<ClosedListEntity> affordable = available.stream()
                .filter(e -> remaining == null || draftTurnService.priceOf(draft, e) <= remaining)
                .toList();

        if (affordable.isEmpty()) {
            throw new IllegalStateException("No quedan Pokémon para elegir automáticamente");
        }

        String randomPokemon = affordable.get(random.nextInt(affordable.size())).getPokemonName();

        // Delegate to the standard pick flow (validates, records, advances turn, generates schedule if completed)
        draftPickCommandHandler.handle(new DraftPickCommand(currentPlayer, randomPokemon, leagueId));
    }
}
