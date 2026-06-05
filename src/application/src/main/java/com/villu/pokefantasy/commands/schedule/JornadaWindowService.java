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
 * Pass {@code null} for {@code LeagueSettings} to use hardcoded defaults (backward compatible).
 */
@Service
public class JornadaWindowService {

    // Hardcoded defaults (used when settings are null or fields are null)
    private static final int    DEFAULT_STEAL_CLOSE_DAY  = 4;       // Thursday
    private static final String DEFAULT_STEAL_CLOSE_TIME = "23:59";
    private static final int    DEFAULT_SWAP_CLOSE_DAY   = 5;       // Friday
    private static final String DEFAULT_SWAP_CLOSE_TIME  = "16:00";

    private final Clock clock;

    /** Production constructor — uses system clock. */
    public JornadaWindowService() {
        this.clock = Clock.systemDefaultZone();
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

    private boolean isWindowOpen(ScheduleEntity schedule, boolean steal, LeagueSettings settings) {
        if (schedule == null || schedule.getJornadas() == null) {
            return false;
        }

        Optional<Jornada> activeOpt = getActiveJornada(schedule);
        if (activeOpt.isEmpty()) {
            return false; // season complete → no window
        }

        Jornada active = activeOpt.get();

        if (active.getStartDate() == null) {
            return true; // no dates configured yet → no time restriction
        }

        // Check that the previous jornada (if any) is fully completed
        int activeRound = active.getRoundNumber();
        if (activeRound > 1) {
            List<Jornada> jornadas = schedule.getJornadas();
            Optional<Jornada> prevOpt = jornadas.stream()
                    .filter(j -> j.getRoundNumber() == activeRound - 1)
                    .findFirst();
            if (prevOpt.isPresent() && !allCompleted(prevOpt.get())) {
                return false; // previous jornada still has pending results
            }
        }

        LocalDateTime deadline = steal
                ? getStealDeadline(active.getStartDate(), settings)
                : getSwapDeadline(active.getStartDate(), settings);

        return LocalDateTime.now(clock).isBefore(deadline);
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
