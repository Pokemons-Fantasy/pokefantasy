package com.villu.pokefantasy.team;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Reglas comunes a todo lo que mueve Pokémon entre equipos y banca después del draft (robo, trade,
 * swap, compra y liberación). Los equipos viven solo en {@code draft.picks}: aquí se modifican los
 * picks en memoria y cada handler guarda draft y liga dentro de su transacción.
 */
@Service
public class TeamTransferService {

    /** Un Pokémon robado o recibido en un trade no puede volver a cambiar de dueño durante este tiempo. */
    public static final Duration TRANSFER_LOCK = Duration.ofDays(7);

    /** {@code round} de los picks comprados en la banca (las rondas del draft empiezan en 1). */
    public static final int BENCH_PURCHASE_ROUND = 0;

    private final DraftRepository draftRepository;
    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final JornadaWindowService jornadaWindowService;

    public TeamTransferService(DraftRepository draftRepository, ScheduleRepository scheduleRepository,
                               LeagueRepository leagueRepository, JornadaWindowService jornadaWindowService) {
        this.draftRepository = draftRepository;
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.jornadaWindowService = jornadaWindowService;
    }

    /** Draft completado y liga, leídos de BD, con la ventana de {@code operation} ya comprobada. */
    public record Market(DraftEntity draft, LeagueEntity league) {}

    public Market openMarket(String leagueId, TeamOperation operation) {
        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(operation.draftNotCompletedMessage));
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("No schedule found for this league"));
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        boolean open = operation.window == TeamOperation.Window.STEAL
                ? jornadaWindowService.isStealWindowOpen(schedule, league.getSettings())
                : jornadaWindowService.isSwapWindowOpen(schedule, league.getSettings());
        if (!open) {
            throw new IllegalStateException(operation.windowClosedMessage);
        }
        return new Market(draft, league);
    }

    /** Miembro de la liga que hace la operación; 403 si no lo es. */
    public LeagueMember requireMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equals(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenOperationException(
                        "User '" + username + "' is not a member of league: " + league.getId()));
    }

    public void requireUnlocked(DraftPick pick) {
        if (pick.getLockedUntil() != null && Instant.now().isBefore(pick.getLockedUntil())) {
            throw new IllegalStateException(
                    "'" + pick.getPokemonName() + "' está bloqueado hasta " + pick.getLockedUntil() + ".");
        }
    }

    /** Cobra {@code amount} monedas, o 409 si no le llegan. */
    public void charge(LeagueMember member, int amount) {
        if (member.getCoinBalance() < amount) {
            throw new IllegalStateException(
                    "No tienes suficientes monedas. Necesitas " + amount +
                    " pero tienes " + member.getCoinBalance() + ".");
        }
        member.setCoinBalance(member.getCoinBalance() - amount);
    }

    public void requireOnBench(DraftEntity draft, String pokemonName) {
        if (draft.ownedPokemonNames().contains(pokemonName.toLowerCase())) {
            throw new IllegalStateException("'" + pokemonName + "' is not available on the bench");
        }
    }

    /**
     * Cambia de dueño un pick (robo, trade) y lo bloquea {@link #TRANSFER_LOCK}. Conserva el precio de
     * robo personalizado, que hereda el nuevo dueño.
     */
    public void transfer(DraftPick pick, String newOwner, Instant now) {
        pick.setUsername(newOwner);
        pick.setLockedUntil(now.plus(TRANSFER_LOCK));
        pick.setPickedAt(now);
    }

    /** Pokémon de la banca al equipo de {@code username} (compra). */
    public DraftPick addFromBench(DraftEntity draft, String username, ClosedListEntity entry, Instant now) {
        DraftPick pick = new DraftPick(username, entry.getPokemonName(), entry.getPokemonId(),
                BENCH_PURCHASE_ROUND, now, null, null);
        draft.getPicks().add(pick);
        return pick;
    }

    /** Sustituye {@code given} por un Pokémon de la banca en la misma posición del equipo (swap). */
    public void replaceWithBench(DraftEntity draft, DraftPick given, ClosedListEntity entry) {
        int index = draft.getPicks().indexOf(given);
        draft.getPicks().set(index, new DraftPick(given.getUsername(), entry.getPokemonName(),
                entry.getPokemonId(), given.getRound(), given.getPickedAt(), null, null));
    }

    /** El Pokémon vuelve a la banca (liberación). */
    public void releaseToBench(DraftEntity draft, DraftPick pick) {
        draft.getPicks().remove(pick);
    }
}
