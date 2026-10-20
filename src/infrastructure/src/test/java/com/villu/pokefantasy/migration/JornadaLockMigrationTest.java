package com.villu.pokefantasy.migration;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JornadaLockMigrationTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    /** Último resultado de la jornada 2: a partir de aquí abre la ventana de la 3. */
    private static final Instant J2_CLOSED = NOW.minus(Duration.ofHours(1));

    // ── regla pura ───────────────────────────────────────────────────────────

    @Test
    void fichadoAntesDeCerrarLaJornadaAnterior_quedaLibre() {
        // lockedUntil = fichaje + 7 días: fichado hace 6 días, en la jornada 2 ya terminada
        Instant lockedUntil = NOW.plus(Duration.ofDays(1));
        assertThat(JornadaLockMigration.lockedThroughJornada(lockedUntil, 3, J2_CLOSED, NOW)).isNull();
    }

    @Test
    void fichadoTrasCerrarLaJornadaAnterior_bloqueadoHastaLaActiva() {
        // Fichado hace 10 minutos, ya en la ventana de la jornada 3
        Instant lockedUntil = NOW.plus(Duration.ofDays(7)).minus(Duration.ofMinutes(10));
        assertThat(JornadaLockMigration.lockedThroughJornada(lockedUntil, 3, J2_CLOSED, NOW)).isEqualTo(3);
    }

    @Test
    void sinResultadosDeLaJornadaAnterior_bloqueadoHastaLaActiva() {
        Instant lockedUntil = NOW.plus(Duration.ofDays(1));
        assertThat(JornadaLockMigration.lockedThroughJornada(lockedUntil, 3, null, NOW)).isEqualTo(3);
        assertThat(JornadaLockMigration.lockedThroughJornada(lockedUntil, 1, null, NOW)).isEqualTo(1);
    }

    @Test
    void bloqueoCaducadoOTemporadaTerminada_libre() {
        assertThat(JornadaLockMigration.lockedThroughJornada(NOW.minusSeconds(1), 3, J2_CLOSED, NOW)).isNull();
        assertThat(JornadaLockMigration.lockedThroughJornada(NOW.plus(Duration.ofDays(1)), null, null, NOW)).isNull();
    }

    // ── migrate() ────────────────────────────────────────────────────────────

    @Test
    void migrate_convierteYLimpiaLockedUntil() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        DraftPick antiguo = pick("crustle", NOW.plus(Duration.ofDays(1)));                       // jornada 2
        DraftPick reciente = pick("golurk", NOW.plus(Duration.ofDays(7)).minus(Duration.ofMinutes(10))); // jornada 3
        DraftPick caducado = pick("klefki", NOW.minus(Duration.ofDays(1)));
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId("l1");
        draft.setPicks(new ArrayList<>(List.of(antiguo, reciente, caducado)));

        when(mongoTemplate.find(any(Query.class), eq(DraftEntity.class))).thenReturn(List.of(draft));
        when(mongoTemplate.findOne(any(Query.class), eq(ScheduleEntity.class))).thenReturn(scheduleWithActive(3));
        ActivityEventEntity lastJ2 = ActivityEventEntity.builder()
                .leagueId("l1").type(ActivityEventType.MATCH_RESULT).roundNumber(2).createdAt(J2_CLOSED).build();
        when(mongoTemplate.findOne(any(Query.class), eq(ActivityEventEntity.class))).thenReturn(lastJ2);

        new JornadaLockMigration(mongoTemplate, new JornadaWindowService()).migrate(NOW);

        verify(mongoTemplate).save(draft);
        assertThat(draft.getPicks()).extracting(DraftPick::getLockedThroughJornada).containsExactly(null, 3, null);
        assertThat(draft.getPicks()).extracting(DraftPick::getLockedUntil).containsOnlyNulls();
    }

    @Test
    void migrate_sinPicksConLockedUntil_noGuarda() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(DraftEntity.class))).thenReturn(List.of());

        new JornadaLockMigration(mongoTemplate, new JornadaWindowService()).migrate(NOW);

        verify(mongoTemplate, never()).save(any());
    }

    private static DraftPick pick(String name, Instant lockedUntil) {
        return new DraftPick("ash", name, 1, 1, NOW.minus(Duration.ofDays(30)), null, lockedUntil);
    }

    private static ScheduleEntity scheduleWithActive(int active) {
        List<Jornada> jornadas = new ArrayList<>();
        for (int round = 1; round <= active; round++) {
            MatchStatus status = round < active ? MatchStatus.COMPLETED : MatchStatus.PENDING;
            jornadas.add(new Jornada(round, new ArrayList<>(List.of(
                    new Match("m" + round, "ash", "brock", null, status))), null));
        }
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(jornadas);
        return schedule;
    }
}
