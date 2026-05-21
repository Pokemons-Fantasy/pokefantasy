package com.villu.pokefantasy.commands.schedule;

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
 *  - stealDeadline  = Thursday 23:59 of the active jornada's ISO week
 *  - swapDeadline   = Friday  16:00 of the active jornada's ISO week
 *  - Both windows are only open if:
 *      1. The previous jornada (if any) has ALL matches COMPLETED
 *      2. The active jornada has a startDate set
 *      3. now < deadline
 */
@Service
public class JornadaWindowService {

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
     * Returns Thursday 23:59:00 of the ISO week that contains {@code startDate}.
     */
    public LocalDateTime getStealDeadline(String startDate) {
        LocalDate d = LocalDate.parse(startDate);
        return d.with(DayOfWeek.THURSDAY).atTime(23, 59);
    }

    /**
     * Returns Friday 16:00:00 of the ISO week that contains {@code startDate}.
     */
    public LocalDateTime getSwapDeadline(String startDate) {
        LocalDate d = LocalDate.parse(startDate);
        return d.with(DayOfWeek.FRIDAY).atTime(16, 0);
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
     * Returns {@code true} when the bench-swap window is open:
     * <ul>
     *   <li>The previous jornada (if any) has all matches COMPLETED.</li>
     *   <li>The active jornada has a {@code startDate}.</li>
     *   <li>Now is before the swap deadline (Friday 16:00).</li>
     * </ul>
     */
    public boolean isSwapWindowOpen(ScheduleEntity schedule) {
        return isWindowOpen(schedule, false);
    }

    /**
     * Returns {@code true} when the Pokémon-steal window is open:
     * same conditions as swap but deadline is Thursday 23:59.
     */
    public boolean isStealWindowOpen(ScheduleEntity schedule) {
        return isWindowOpen(schedule, true);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isWindowOpen(ScheduleEntity schedule, boolean steal) {
        if (schedule == null || schedule.getJornadas() == null) {
            return false;
        }

        Optional<Jornada> activeOpt = getActiveJornada(schedule);
        if (activeOpt.isEmpty()) {
            return false; // season complete → no window
        }

        Jornada active = activeOpt.get();

        if (active.getStartDate() == null) {
            return false; // admin hasn't set seasonStartDate yet
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
                ? getStealDeadline(active.getStartDate())
                : getSwapDeadline(active.getStartDate());

        return LocalDateTime.now(clock).isBefore(deadline);
    }

    private boolean allCompleted(Jornada jornada) {
        if (jornada.getMatches() == null || jornada.getMatches().isEmpty()) {
            return true;
        }
        return jornada.getMatches().stream()
                .allMatch(m -> MatchStatus.COMPLETED.equals(m.getStatus()));
    }
}
