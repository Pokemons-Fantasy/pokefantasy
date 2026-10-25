package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.AutoImportTarget;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAutoImportTargetsCommandHandlerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private UserRepository userRepository;

    private Jornada active;
    private ScheduleEntity schedule;

    private static UserEntity user(String name, String showdownId) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setShowdownId(showdownId);
        return u;
    }

    /** Mercado de la jornada del lunes 19-10 cerrado el viernes 23-10 a las 16:00 (ajustes por defecto). */
    private ListAutoImportTargetsCommandHandler handlerAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(JornadaWindowService.LEAGUE_ZONE).toInstant(), JornadaWindowService.LEAGUE_ZONE);
        return new ListAutoImportTargetsCommandHandler(scheduleRepository, leagueRepository, userRepository,
                new JornadaWindowService(clock));
    }

    @BeforeEach
    void setUp() {
        Jornada done = new Jornada(1, new ArrayList<>(List.of(new Match("m0", "ash", "misty", "ash", MatchStatus.COMPLETED))),
                "2026-10-12");
        active = new Jornada(2, new ArrayList<>(List.of(
                new Match("m1", "ash", "misty", null, MatchStatus.PENDING),
                new Match("m2", "brock", "gary", null, MatchStatus.PENDING),
                new Match("m3", "ash", "brock", "ash", MatchStatus.COMPLETED))), "2026-10-19");
        Jornada future = new Jornada(3, new ArrayList<>(List.of(new Match("m4", "ash", "misty", null, MatchStatus.PENDING))),
                "2026-10-26");
        schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(done, active, future)));
        lenient().when(scheduleRepository.findAll()).thenReturn(List.of(schedule));

        LeagueEntity league = new LeagueEntity();
        league.setId("l1");
        league.setSettings(LeagueSettings.defaults());
        lenient().when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));
        lenient().when(userRepository.findByUsernames(anyCollection())).thenReturn(List.of(
                user("ash", "ashketchum"), user("misty", "mistyw"), user("brock", "brockr"), user("gary", null)));
    }

    @Test
    void marketClosed_onlyPendingMatchesOfActiveJornadaWithBothLinked() {
        assertThat(handlerAt(LocalDateTime.of(2026, 10, 24, 12, 0)).handle(new ListAutoImportTargetsCommand()))
                .containsExactly(new AutoImportTarget("l1", "m1"));
    }

    @Test
    void marketStillOpen_nothing() {
        assertThat(handlerAt(LocalDateTime.of(2026, 10, 23, 15, 59)).handle(new ListAutoImportTargetsCommand())).isEmpty();
    }

    @Test
    void jornadaWithoutDate_alwaysCandidate() {
        active.setStartDate(null);

        assertThat(handlerAt(LocalDateTime.of(2026, 10, 20, 9, 0)).handle(new ListAutoImportTargetsCommand()))
                .containsExactly(new AutoImportTarget("l1", "m1"));
    }

    @Test
    void leagueMissing_orSeasonOver_ignored() {
        ScheduleEntity orphan = new ScheduleEntity();
        orphan.setLeagueId("gone");
        orphan.setJornadas(new ArrayList<>(List.of(new Jornada(1,
                new ArrayList<>(List.of(new Match("x", "ash", "misty", null, MatchStatus.PENDING))), null))));
        ScheduleEntity over = new ScheduleEntity();
        over.setLeagueId("l1");
        over.setJornadas(new ArrayList<>(List.of(new Jornada(1,
                new ArrayList<>(List.of(new Match("y", "ash", "misty", "ash", MatchStatus.COMPLETED))), null))));
        when(scheduleRepository.findAll()).thenReturn(List.of(orphan, over));
        when(leagueRepository.findById("gone")).thenReturn(Optional.empty());

        assertThat(handlerAt(LocalDateTime.of(2026, 10, 24, 12, 0)).handle(new ListAutoImportTargetsCommand())).isEmpty();
    }

    @Test
    void commandType() {
        assertThat(handlerAt(LocalDateTime.of(2026, 10, 24, 12, 0)).commandType()).isEqualTo(ListAutoImportTargetsCommand.class);
    }
}
