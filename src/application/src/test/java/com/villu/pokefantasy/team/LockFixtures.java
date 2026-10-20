package com.villu.pokefantasy.team;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

/** Calendarios y stubs para los tests que pasan por el bloqueo hasta fin de jornada. */
public final class LockFixtures {

    private LockFixtures() {}

    /**
     * En un {@link JornadaWindowService} mockeado, la jornada activa y si una jornada está completada se
     * calculan de verdad (no dependen del reloj); las ventanas siguen mockeadas en cada test.
     */
    public static void realJornadaRules(JornadaWindowService mock) {
        lenient().when(mock.getActiveJornada(any())).thenCallRealMethod();
        lenient().when(mock.isJornadaCompleted(any(), anyInt())).thenCallRealMethod();
    }

    /** Jornadas 1..{@code active}: las anteriores completadas, {@code active} con un partido pendiente. */
    public static ScheduleEntity scheduleWithActiveJornada(String leagueId, int active) {
        List<Jornada> jornadas = new ArrayList<>();
        for (int round = 1; round <= active; round++) {
            MatchStatus status = round < active ? MatchStatus.COMPLETED : MatchStatus.PENDING;
            Match match = new Match("m" + round, "ash", "brock", status == MatchStatus.COMPLETED ? "ash" : null, status);
            jornadas.add(new Jornada(round, new ArrayList<>(List.of(match)), null));
        }
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(leagueId);
        schedule.setJornadas(jornadas);
        return schedule;
    }
}
