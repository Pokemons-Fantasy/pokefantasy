package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JornadaWindowServiceTest {

    private static final String START_DATE = "2026-06-06"; // Saturday
    // Thursday of that ISO week = 2026-06-04
    // Friday of that ISO week  = 2026-06-05

    /** Build a service with a fixed clock at the given LocalDateTime (system default zone). */
    private JornadaWindowService serviceAt(LocalDateTime now) {
        Clock fixed = Clock.fixed(
                now.atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new JornadaWindowService(fixed);
    }

    private JornadaWindowService service() {
        return serviceAt(LocalDateTime.of(2026, 6, 3, 10, 0)); // Tuesday inside window
    }

    // -------------------------------------------------------------------------
    // getStealDeadline / getSwapDeadline
    // -------------------------------------------------------------------------

    @Test
    void getStealDeadline_returnsThursday2359() {
        LocalDateTime deadline = service().getStealDeadline(START_DATE);
        assertThat(deadline).isEqualTo(LocalDateTime.of(2026, 6, 4, 23, 59));
    }

    @Test
    void getSwapDeadline_returnsFriday1600() {
        LocalDateTime deadline = service().getSwapDeadline(START_DATE);
        assertThat(deadline).isEqualTo(LocalDateTime.of(2026, 6, 5, 16, 0));
    }

    // -------------------------------------------------------------------------
    // getActiveJornada
    // -------------------------------------------------------------------------

    @Test
    void getActiveJornada_allCompleted_returnsEmpty() {
        ScheduleEntity schedule = scheduleWithJornadas(
                completedJornada(1),
                completedJornada(2));

        Optional<Jornada> result = service().getActiveJornada(schedule);
        assertThat(result).isEmpty();
    }

    @Test
    void getActiveJornada_firstPending_returnsFirst() {
        ScheduleEntity schedule = scheduleWithJornadas(
                pendingJornada(1, START_DATE),
                pendingJornada(2, "2026-06-13"));

        Optional<Jornada> result = service().getActiveJornada(schedule);
        assertThat(result).isPresent();
        assertThat(result.get().getRoundNumber()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // isSwapWindowOpen
    // -------------------------------------------------------------------------

    @Test
    void isSwapWindowOpen_beforeDeadline_returnsTrue() {
        // Wednesday 10:00 - before Friday 16:00
        JornadaWindowService svc = serviceAt(LocalDateTime.of(2026, 6, 3, 10, 0));
        ScheduleEntity schedule = scheduleWithJornadas(pendingJornada(1, START_DATE));

        assertThat(svc.isSwapWindowOpen(schedule)).isTrue();
    }

    @Test
    void isSwapWindowOpen_afterDeadline_returnsFalse() {
        // Friday 17:00 - after Friday 16:00
        JornadaWindowService svc = serviceAt(LocalDateTime.of(2026, 6, 5, 17, 0));
        ScheduleEntity schedule = scheduleWithJornadas(pendingJornada(1, START_DATE));

        assertThat(svc.isSwapWindowOpen(schedule)).isFalse();
    }

    @Test
    void isSwapWindowOpen_previousJornadaNotCompleted_returnsFalse() {
        // Wednesday of the second jornada's week - inside window timing-wise,
        // but round 1 (previous) still has pending matches -> must be blocked.
        // Trick: put round 2 first in the list so getActiveJornada picks it as "active".
        JornadaWindowService svc = serviceAt(LocalDateTime.of(2026, 6, 10, 10, 0));

        Jornada j2Active = jornada(2, "2026-06-13", MatchStatus.PENDING);
        Jornada j1Pending = jornada(1, START_DATE, MatchStatus.PENDING); // prev not completed

        ScheduleEntity sch = new ScheduleEntity();
        sch.setJornadas(new ArrayList<>(List.of(j2Active, j1Pending)));

        assertThat(svc.isSwapWindowOpen(sch)).isFalse();
    }

    @Test
    void isSwapWindowOpen_noStartDate_returnsFalse() {
        JornadaWindowService svc = serviceAt(LocalDateTime.of(2026, 6, 3, 10, 0));
        Jornada j = jornada(1, null, MatchStatus.PENDING); // no startDate
        ScheduleEntity schedule = scheduleWithJornadas(j);

        assertThat(svc.isSwapWindowOpen(schedule)).isFalse();
    }

    // -------------------------------------------------------------------------
    // isStealWindowOpen
    // -------------------------------------------------------------------------

    @Test
    void isStealWindowOpen_beforeThursday_returnsTrue() {
        // Wednesday 10:00 - before Thursday 23:59
        JornadaWindowService svc = serviceAt(LocalDateTime.of(2026, 6, 3, 10, 0));
        ScheduleEntity schedule = scheduleWithJornadas(pendingJornada(1, START_DATE));

        assertThat(svc.isStealWindowOpen(schedule)).isTrue();
    }

    @Test
    void isStealWindowOpen_afterThursday_returnsFalse() {
        // Friday 00:01 - after Thursday 23:59
        JornadaWindowService svc = serviceAt(LocalDateTime.of(2026, 6, 5, 0, 1));
        ScheduleEntity schedule = scheduleWithJornadas(pendingJornada(1, START_DATE));

        assertThat(svc.isStealWindowOpen(schedule)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ScheduleEntity scheduleWithJornadas(Jornada... jornadas) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(jornadas)));
        return schedule;
    }

    private Jornada jornada(int round, String startDate, MatchStatus status) {
        Match match = new Match("m" + round, "ash", "brock", null, status);
        return new Jornada(round, new ArrayList<>(List.of(match)), startDate);
    }

    private Jornada pendingJornada(int round, String startDate) {
        return jornada(round, startDate, MatchStatus.PENDING);
    }

    private Jornada completedJornada(int round) {
        Match match = new Match("m" + round, "ash", "brock", "ash", MatchStatus.COMPLETED);
        return new Jornada(round, new ArrayList<>(List.of(match)), "2026-05-01");
    }
}
