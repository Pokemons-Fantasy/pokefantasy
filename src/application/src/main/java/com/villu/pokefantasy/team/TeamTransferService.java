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

import java.time.Instant;

/**
 * Reglas comunes a todo lo que mueve Pokémon entre equipos y banca después del draft (robo, trade,
 * swap, compra y liberación). Los equipos viven solo en {@code draft.picks}: aquí se modifican los
 * picks en memoria y cada handler guarda draft y liga dentro de su transacción.
 * <p>
 * Un Pokémon que entra en un equipo después del draft (robo, trade, compra o swap con la banca) no puede
 * cambiar de dueño ni volver a la banca hasta que termine la jornada en la que entró
 * ({@link DraftPick#getLockedThroughJornada()}): se libera al registrarse su último resultado.
 */
@Service
public class TeamTransferService {

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

    /** Draft completado, liga y calendario, leídos de BD, con la ventana de {@code operation} ya comprobada. */
    public record Market(DraftEntity draft, LeagueEntity league, ScheduleEntity schedule) {}

    public Market openMarket(String leagueId, TeamOperation operation) {
        DraftEntity draft = draftRepository.findLatestByLeagueId(leagueId)
                .filter(d -> d.getStatus() == DraftStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException(operation.draftNotCompletedMessage));
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(leagueId)
                .orElseThrow(() -> new IllegalStateException("Esta liga aún no tiene calendario"));
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        boolean open = operation.window == TeamOperation.Window.STEAL
                ? jornadaWindowService.isStealWindowOpen(schedule, league.getSettings())
                : jornadaWindowService.isSwapWindowOpen(schedule, league.getSettings());
        if (!open) {
            throw new IllegalStateException(operation.windowClosedMessage);
        }
        return new Market(draft, league, schedule);
    }

    /** Miembro de la liga que hace la operación; 403 si no lo es. */
    public LeagueMember requireMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equals(m.getUsername()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenOperationException(
                        "No eres miembro de esta liga"));
    }

    /** Bloqueado mientras la jornada en la que entró al equipo tenga algún resultado pendiente. */
    public boolean isLocked(DraftPick pick, ScheduleEntity schedule) {
        Integer jornada = pick.getLockedThroughJornada();
        return jornada != null && !jornadaWindowService.isJornadaCompleted(schedule, jornada);
    }

    /** 409 si {@code pick} sigue bloqueado (ver {@link #isLocked}). */
    public void requireUnlocked(DraftPick pick, ScheduleEntity schedule) {
        if (isLocked(pick, schedule)) {
            throw new IllegalStateException("'" + pick.getPokemonName() + "' está bloqueado hasta que termine la jornada "
                    + pick.getLockedThroughJornada() + ".");
        }
    }

    /** Jornada activa, a la que se ata el bloqueo de lo que entra ahora en un equipo; 409 si la temporada acabó. */
    public int currentJornada(ScheduleEntity schedule) {
        return jornadaWindowService.getActiveJornada(schedule)
                .map(ScheduleEntity.Jornada::getRoundNumber)
                .orElseThrow(() -> new IllegalStateException("La temporada ha terminado"));
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
            throw new IllegalStateException("'" + pokemonName + "' no está en el banquillo");
        }
    }

    /**
     * Cambia de dueño un pick (robo, trade) y lo bloquea hasta que termine {@code jornada}. Conserva el precio
     * de robo personalizado, que hereda el nuevo dueño.
     */
    public void transfer(DraftPick pick, String newOwner, int jornada, Instant now) {
        pick.setUsername(newOwner);
        lock(pick, jornada);
        pick.setPickedAt(now);
    }

    /** Pokémon de la banca al equipo de {@code username} (compra), bloqueado hasta que termine {@code jornada}. */
    public DraftPick addFromBench(DraftEntity draft, String username, ClosedListEntity entry, int jornada, Instant now) {
        DraftPick pick = new DraftPick(username, entry.getPokemonName(), entry.getPokemonId(),
                BENCH_PURCHASE_ROUND, now, null, null);
        lock(pick, jornada);
        draft.getPicks().add(pick);
        return pick;
    }

    /**
     * Sustituye {@code given} por un Pokémon de la banca en la misma posición del equipo (swap). El que entra
     * queda bloqueado hasta que termine {@code jornada}.
     */
    public void replaceWithBench(DraftEntity draft, DraftPick given, ClosedListEntity entry, int jornada) {
        int index = draft.getPicks().indexOf(given);
        DraftPick pick = new DraftPick(given.getUsername(), entry.getPokemonName(),
                entry.getPokemonId(), given.getRound(), given.getPickedAt(), null, null);
        lock(pick, jornada);
        draft.getPicks().set(index, pick);
    }

    private static void lock(DraftPick pick, int jornada) {
        pick.setLockedThroughJornada(jornada);
        pick.setLockedUntil(null);
    }

    /** El Pokémon vuelve a la banca (liberación). */
    public void releaseToBench(DraftEntity draft, DraftPick pick) {
        draft.getPicks().remove(pick);
    }
}
