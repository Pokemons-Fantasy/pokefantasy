package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.league.LeaguePushTokens;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.MatchLineup;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftScheduleCommandHandlerTest {

    private static final String LEAGUE_ID = "l1";
    private static final String ADMIN = "ash";
    /** Miércoles. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 16, 12, 0);

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueAdminGuard leagueAdminGuard;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private PushNotificationPort pushNotificationPort;

    private LeagueEntity league;
    private ScheduleEntity schedule;

    @BeforeEach
    void setUp() {
        league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setName("Liga Kanto");
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0), new LeagueMember("brock", LeagueRole.USER, 0),
                new LeagueMember("misty", LeagueRole.USER, 0), new LeagueMember("gary", LeagueRole.USER, 0)));
        league.setSettings(LeagueSettings.builder().seasonStartDate("2026-12-05").build());
        lenient().when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, ADMIN)).thenReturn(league);
        for (String name : List.of("ash", "brock", "misty", "gary")) {
            UserEntity u = new UserEntity();
            u.setName(name);
            u.setFcmTokens(List.of("t-" + name));
            lenient().when(userRepository.findByUsername(name)).thenReturn(u);
        }
        // 1 jugada; 2 activa; 3 y 4 futuras.
        schedule = schedule(
                jornada(1, "2026-12-05", MatchStatus.COMPLETED),
                jornada(2, "2026-12-12", MatchStatus.PENDING),
                jornada(3, "2026-12-19", MatchStatus.PENDING),
                jornada(4, "2026-12-26", MatchStatus.PENDING));
        lenient().when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenAnswer(inv -> Optional.ofNullable(schedule));
    }

    private ShiftScheduleCommandHandler handlerAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(JornadaWindowService.LEAGUE_ZONE).toInstant(), JornadaWindowService.LEAGUE_ZONE);
        return new ShiftScheduleCommandHandler(scheduleRepository, leagueRepository, leagueAdminGuard,
                new JornadaWindowService(clock), activityEventRepository, new LeaguePushTokens(userRepository),
                pushNotificationPort);
    }

    private ShiftScheduleCommandHandler handler() {
        return handlerAt(NOW);
    }

    private static Jornada jornada(int round, String date, MatchStatus status) {
        Match a = new Match("m" + round + "a", "ash", "brock", status == MatchStatus.COMPLETED ? "ash" : null, status);
        Match b = new Match("m" + round + "b", "misty", "gary", status == MatchStatus.COMPLETED ? "misty" : null, status);
        return new Jornada(round, new ArrayList<>(List.of(a, b)), date);
    }

    private static ScheduleEntity schedule(Jornada... jornadas) {
        ScheduleEntity s = new ScheduleEntity();
        s.setLeagueId(LEAGUE_ID);
        s.setJornadas(new ArrayList<>(Arrays.asList(jornadas)));
        return s;
    }

    private List<String> dates() {
        return schedule.getJornadas().stream().map(Jornada::getStartDate).toList();
    }

    private static MatchLineup lineup() {
        return new MatchLineup(List.of("pikachu"), null, List.of(), Instant.now());
    }

    @Test
    void shift_futureJornada_movesItAndFollowing() {
        handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, 2, ADMIN));

        assertThat(dates()).containsExactly("2026-12-05", "2026-12-12", "2027-01-02", "2027-01-09");
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void shift_activeWithClosedMarket_clearsLineupsAndNotifiesThem() {
        Jornada active = schedule.getJornadas().get(1);
        Match ashBrock = active.getMatches().get(0);
        Match mistyGary = active.getMatches().get(1);
        ashBrock.setPlayer1Lineup(lineup());
        mistyGary.setPlayer1Lineup(lineup());
        mistyGary.setPlayer2Lineup(lineup());

        handlerAt(LocalDateTime.of(2026, 12, 12, 18, 0)).handle(new ShiftScheduleCommand(LEAGUE_ID, 2, 1, ADMIN));

        assertThat(active.getStartDate()).isEqualTo("2026-12-19");
        assertThat(ashBrock.getPlayer1Lineup()).isNull();
        assertThat(mistyGary.getPlayer1Lineup()).isNull();
        assertThat(mistyGary.getPlayer2Lineup()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationPort, times(2)).send(tokens.capture(), messages.capture());
        int lineupPush = messages.getAllValues().get(0).title().equals("Alineación anulada") ? 0 : 1;
        assertThat(messages.getAllValues().get(lineupPush).title()).isEqualTo("Alineación anulada");
        assertThat(tokens.getAllValues().get(lineupPush)).containsExactlyInAnyOrder("t-ash", "t-misty", "t-gary");
    }

    @Test
    void shift_notifiesLeagueAndRecordsEvent() {
        handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, 2, ADMIN));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationPort).send(tokens.capture(), message.capture());
        assertThat(tokens.getValue()).containsExactly("t-ash", "t-brock", "t-misty", "t-gary");
        assertThat(message.getValue().title()).isEqualTo("Calendario aplazado");
        assertThat(message.getValue().body()).contains("la jornada 3 pasa al fin de semana del 02/01");
        assertThat(message.getValue().path()).isEqualTo("/leagues/l1/schedule");

        ArgumentCaptor<ActivityEventEntity> event = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository).save(event.capture());
        assertThat(event.getValue().getType()).isEqualTo(ActivityEventType.SCHEDULE_SHIFTED);
        assertThat(event.getValue().getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(event.getValue().getActorUsername()).isEqualTo(ADMIN);
        assertThat(event.getValue().getRoundNumber()).isEqualTo(3);
        assertThat(event.getValue().getWeeks()).isEqualTo(2);
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void shift_negative_titleSaysAdelantado() {
        schedule.getJornadas().get(3).setStartDate("2027-01-02"); // un parón anterior la dejó una semana más tarde

        handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 4, -1, ADMIN));

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationPort).send(any(), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Calendario adelantado");
        assertThat(schedule.getJornadas().get(3).getStartDate()).isEqualTo("2026-12-26");
    }

    @Test
    void shift_negative_intoPreviousWeek_rejected() {
        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, -1, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jornada 2");
        assertThat(dates()).containsExactly("2026-12-05", "2026-12-12", "2026-12-19", "2026-12-26");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shift_negative_intoPastWeek_rejected() {
        schedule = schedule(
                jornada(1, "2026-11-21", MatchStatus.COMPLETED),
                jornada(2, "2026-11-28", MatchStatus.COMPLETED),
                jornada(3, "2026-12-26", MatchStatus.PENDING),
                jornada(4, "2027-01-02", MatchStatus.PENDING));

        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, -3, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("semana ya pasada");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shift_activeWithResult_rejected() {
        schedule.getJornadas().get(1).getMatches().get(0).setStatus(MatchStatus.COMPLETED);

        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 2, 1, ADMIN)))
                .isInstanceOf(IllegalStateException.class);
        verify(scheduleRepository, never()).save(any());

        handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, 1, ADMIN));
        assertThat(dates()).containsExactly("2026-12-05", "2026-12-12", "2026-12-26", "2027-01-02");
    }

    @Test
    void shift_playedJornada_rejected() {
        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 1, 1, ADMIN)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shift_weeksOutOfRange_rejected() {
        for (Integer weeks : Arrays.asList(0, 13, -13, null)) {
            assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, weeks, ADMIN)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, null, 1, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shift_unknownJornada_rejected() {
        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 9, 1, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shift_noSchedule_rejected() {
        schedule = null;

        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, 1, ADMIN)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shift_jornadaWithoutDate_rejected() {
        schedule.getJornadas().get(3).setStartDate(null);

        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, 1, ADMIN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fecha de inicio");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shift_fromRound1_updatesSeasonStartDate() {
        schedule = schedule(
                jornada(1, "2026-12-19", MatchStatus.PENDING),
                jornada(2, "2026-12-26", MatchStatus.PENDING));
        league.getSettings().setSeasonStartDate("2026-12-19");

        handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 1, 1, ADMIN));

        assertThat(dates()).containsExactly("2026-12-26", "2027-01-02");
        assertThat(league.getSettings().getSeasonStartDate()).isEqualTo("2026-12-26");
        verify(leagueRepository).save(league);
    }

    @Test
    void shift_notAdmin_propagatesForbidden() {
        when(leagueAdminGuard.requireLeagueAdmin(LEAGUE_ID, "misty")).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> handler().handle(new ShiftScheduleCommand(LEAGUE_ID, 3, 1, "misty")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void commandType_isShiftScheduleCommand() {
        assertThat(handler().commandType()).isEqualTo(ShiftScheduleCommand.class);
    }
}
