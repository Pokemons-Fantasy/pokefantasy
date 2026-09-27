package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Determines time-window eligibility for bench swaps and Pokémon steals.
 *
 * Rules:
 *  - stealDeadline  = configurable day/time (default: Thursday 23:59) of the active jornada's ISO week
 *  - swapDeadline   = configurable day/time (default: Friday  16:00) of the active jornada's ISO week
 *  - Both windows are only open if:
 *      1. The previous jornada (if any) has ALL matches COMPLETED
 *      2. The active jornada has a startDate set
 *      3. now < deadline
 *
 * Deadlines are wall-clock times in {@link #LEAGUE_ZONE} (Spain), independent of the server's
 * timezone — Render runs in UTC, which would otherwise shift them by 1–2 hours (DST aware).
 *
 * Pass {@code null} for {@code LeagueSettings} to use hardcoded defaults (backward compatible).
 */
@Service
public class JornadaWindowService {

    // Hardcoded defaults (used when settings are null or fields are null)
    private static final int    DEFAULT_STEAL_CLOSE_DAY  = 4;       // Thursday
    private static final String DEFAULT_STEAL_CLOSE_TIME = "23:59";
    private static final int    DEFAULT_SWAP_CLOSE_DAY   = 5;       // Friday
    private static final String DEFAULT_SWAP_CLOSE_TIME  = "16:00";

    /** Timezone in which the configured deadline day/time are interpreted. */
    public static final ZoneId LEAGUE_ZONE = ZoneId.of("Europe/Madrid");

    private final Clock clock;

    /** Production constructor — uses system clock. */
    public JornadaWindowService() {
        this.clock = Clock.system(LEAGUE_ZONE);
    }

    /** Test constructor — allows injecting a fixed clock. */
    JornadaWindowService(Clock clock) {
        this.clock = clock;
    }

    // -------------------------------------------------------------------------
    // Deadline calculators
    // -------------------------------------------------------------------------

    /**
     * Returns the steal deadline for the ISO week containing {@code startDate},
     * using the day/time configured in {@code settings} (or Thursday 23:59 if null).
     */
    public LocalDateTime getStealDeadline(String startDate, LeagueSettings settings) {
        int day  = settings != null && settings.getStealWindowCloseDay()  != null
                   ? settings.getStealWindowCloseDay()  : DEFAULT_STEAL_CLOSE_DAY;
        String t = settings != null && settings.getStealWindowCloseTime() != null
                   ? settings.getStealWindowCloseTime() : DEFAULT_STEAL_CLOSE_TIME;
        return buildDeadline(startDate, day, t);
    }

    /**
     * Returns the swap deadline for the ISO week containing {@code startDate},
     * using the day/time configured in {@code settings} (or Friday 16:00 if null).
     */
    public LocalDateTime getSwapDeadline(String startDate, LeagueSettings settings) {
        int day  = settings != null && settings.getSwapWindowCloseDay()  != null
                   ? settings.getSwapWindowCloseDay()  : DEFAULT_SWAP_CLOSE_DAY;
        String t = settings != null && settings.getSwapWindowCloseTime() != null
                   ? settings.getSwapWindowCloseTime() : DEFAULT_SWAP_CLOSE_TIME;
        return buildDeadline(startDate, day, t);
    }

    // -------------------------------------------------------------------------
    // Active jornada
    // -------------------------------------------------------------------------

    /**
     * Returns the first jornada that has at least one non-COMPLETED match.
     * Returns empty if all jornadas are fully COMPLETED (season over).
     */
    public Optional<Jornada> getActiveJornada(ScheduleEntity schedule) {
        if (schedule == null || schedule.getJornadas() == null) {
            return Optional.empty();
        }
        return schedule.getJornadas().stream()
                .filter(j -> !allCompleted(j))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // Window checks
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the bench-swap window is open.
     * Uses default Friday 16:00 deadline (backward-compatible overload).
     */
    public boolean isSwapWindowOpen(ScheduleEntity schedule) {
        return isWindowOpen(schedule, false, null);
    }

    /**
     * Returns {@code true} when the bench-swap window is open,
     * using the day/time configured in {@code settings}.
     */
    public boolean isSwapWindowOpen(ScheduleEntity schedule, LeagueSettings settings) {
        return isWindowOpen(schedule, false, settings);
    }

    /**
     * Returns {@code true} when the Pokémon-steal window is open.
     * Uses default Thursday 23:59 deadline (backward-compatible overload).
     */
    public boolean isStealWindowOpen(ScheduleEntity schedule) {
        return isWindowOpen(schedule, true, null);
    }

    /**
     * Returns {@code true} when the Pokémon-steal window is open,
     * using the day/time configured in {@code settings}.
     */
    public boolean isStealWindowOpen(ScheduleEntity schedule, LeagueSettings settings) {
        return isWindowOpen(schedule, true, settings);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Cierre de la ventana de robos ({@code steal}) o de swaps de la jornada activa, si ahora mismo está
     * abierta y tiene hora de cierre. Vacío si está cerrada o si no hay fechas configuradas (abierta sin
     * límite).
     */
    public Optional<LocalDateTime> openWindowDeadline(ScheduleEntity schedule, boolean steal, LeagueSettings settings) {
        return activeJornadaWithOpenWindow(schedule)
                .filter(active -> active.getStartDate() != null)
                .map(active -> steal
                        ? getStealDeadline(active.getStartDate(), settings)
                        : getSwapDeadline(active.getStartDate(), settings))
                .filter(deadline -> now().isBefore(deadline));
    }

    /** Hora actual en {@link #LEAGUE_ZONE}. */
    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(LEAGUE_ZONE));
    }

    private boolean isWindowOpen(ScheduleEntity schedule, boolean steal, LeagueSettings settings) {
        Optional<Jornada> active = activeJornadaWithOpenWindow(schedule);
        if (active.isEmpty()) {
            return false;
        }
        if (active.get().getStartDate() == null) {
            return true; // no dates configured yet → no time restriction
        }
        return openWindowDeadline(schedule, steal, settings).isPresent();
    }

    /**
     * Jornada activa si su ventana puede estar abierta: hay temporada en curso y la jornada anterior (si
     * la hay) tiene todos los resultados. No mira la hora de cierre.
     */
    private Optional<Jornada> activeJornadaWithOpenWindow(ScheduleEntity schedule) {
        if (schedule == null || schedule.getJornadas() == null) {
            return Optional.empty();
        }

        Optional<Jornada> activeOpt = getActiveJornada(schedule);
        if (activeOpt.isEmpty()) {
            return Optional.empty(); // season complete → no window
        }

        Jornada active = activeOpt.get();
        int activeRound = active.getRoundNumber();
        if (active.getStartDate() != null && activeRound > 1) {
            Optional<Jornada> prevOpt = schedule.getJornadas().stream()
                    .filter(j -> j.getRoundNumber() == activeRound - 1)
                    .findFirst();
            if (prevOpt.isPresent() && !allCompleted(prevOpt.get())) {
                return Optional.empty(); // previous jornada still has pending results
            }
        }
        return activeOpt;
    }

    private LocalDateTime buildDeadline(String startDate, int dayOfWeek, String time) {
        String[] parts = time.split(":");
        int hour   = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return LocalDate.parse(startDate).with(DayOfWeek.of(dayOfWeek)).atTime(hour, minute);
    }

    private boolean allCompleted(Jornada jornada) {
        if (jornada.getMatches() == null || jornada.getMatches().isEmpty()) {
            return true;
        }
        return jornada.getMatches().stream()
                .allMatch(m -> MatchStatus.COMPLETED.equals(m.getStatus()));
    }
}
