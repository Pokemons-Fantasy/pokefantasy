package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WindowReminderServiceTest {

    // Semana del 1 al 7 de junio de 2026: robos cierran el jueves 4 a las 23:59, swaps el viernes 5 a las 16:00.
    private static final String START_DATE = "2026-06-06";
    private static final LocalDateTime THURSDAY_NIGHT = LocalDateTime.of(2026, 6, 4, 21, 30);
    private static final LocalDateTime FRIDAY_NOON = LocalDateTime.of(2026, 6, 5, 13, 30);

    @Mock private UserRepository userRepository;
    @Mock private PushNotificationPort pushNotificationPort;

    private ScheduleEntity schedule;
    private Jornada jornada;
    private LeagueEntity league;

    @BeforeEach
    void setUp() {
        jornada = new Jornada(1, new ArrayList<>(List.of(new Match("m1", "ash", "brock", null, MatchStatus.PENDING))),
                START_DATE);
        schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(jornada)));

        league = new LeagueEntity();
        league.setId("l1");
        league.setName("Liga Kanto");
        league.setMembers(new ArrayList<>(List.of(
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("brock", LeagueRole.USER, 0),
                new LeagueMember("ghost", LeagueRole.USER, 0))));
        UserEntity ash = new UserEntity();
        ash.setFcmTokens(new ArrayList<>(List.of("ash-phone")));
        UserEntity brock = new UserEntity();
        brock.setFcmTokens(new ArrayList<>(List.of("brock-phone", "brock-tablet")));
        lenient().when(userRepository.findByUsername("ash")).thenReturn(ash);
        lenient().when(userRepository.findByUsername("brock")).thenReturn(brock);
    }

    private WindowReminderService serviceAt(LocalDateTime now) {
        Clock fixed = Clock.fixed(now.atZone(JornadaWindowService.LEAGUE_ZONE).toInstant(),
                JornadaWindowService.LEAGUE_ZONE);
        return new WindowReminderService(new JornadaWindowService(fixed), userRepository, pushNotificationPort);
    }

    @Test
    void stealWindowClosingSoon_isDue_swapNotYet() {
        assertThat(serviceAt(THURSDAY_NIGHT).due(schedule, null))
                .singleElement()
                .satisfies(due -> {
                    assertThat(due.window()).isEqualTo(WindowReminderService.Window.STEAL);
                    assertThat(due.deadline()).isEqualTo(LocalDateTime.of(2026, 6, 4, 23, 59));
                });
    }

    @Test
    void swapWindowClosingSoon_isDue_afterStealClosed() {
        assertThat(serviceAt(FRIDAY_NOON).due(schedule, null))
                .singleElement()
                .satisfies(due -> assertThat(due.window()).isEqualTo(WindowReminderService.Window.SWAP));
    }

    @Test
    void earlyInTheWeek_nothingDue() {
        assertThat(serviceAt(LocalDateTime.of(2026, 6, 2, 10, 0)).due(schedule, null)).isEmpty();
    }

    @Test
    void noDatesConfigured_nothingDue() {
        jornada.setStartDate(null);

        assertThat(serviceAt(THURSDAY_NIGHT).due(schedule, null)).isEmpty();
    }

    @Test
    void seasonFinished_nothingDue() {
        jornada.getMatches().get(0).setStatus(MatchStatus.COMPLETED);

        assertThat(serviceAt(THURSDAY_NIGHT).due(schedule, null)).isEmpty();
    }

    @Test
    void sendDue_pushesToEveryMemberWithTokens_andMarksTheJornada() {
        boolean sent = serviceAt(THURSDAY_NIGHT).sendDue(schedule, league);

        assertThat(sent).isTrue();
        verify(pushNotificationPort).send(eq(List.of("ash-phone", "brock-phone", "brock-tablet")),
                contains("robos"), contains("Liga Kanto: tienes hasta las 23:59"));
        assertThat(jornada.getStealReminderSentFor()).isEqualTo("2026-06-04T23:59");
        assertThat(jornada.getSwapReminderSentFor()).isNull();
    }

    @Test
    void alreadyReminded_notRepeated() {
        jornada.setStealReminderSentFor("2026-06-04T23:59");

        assertThat(serviceAt(THURSDAY_NIGHT).sendDue(schedule, league)).isFalse();
        verify(pushNotificationPort, never()).send(anyList(), anyString(), anyString());
    }

    @Test
    void deadlineChangedAfterReminder_remindsAgain() {
        jornada.setStealReminderSentFor("2026-06-04T22:00");

        assertThat(serviceAt(THURSDAY_NIGHT).sendDue(schedule, league)).isTrue();
        assertThat(jornada.getStealReminderSentFor()).isEqualTo("2026-06-04T23:59");
    }

    @Test
    void swapReminder_usesSwapText() {
        serviceAt(FRIDAY_NOON).sendDue(schedule, league);

        verify(pushNotificationPort).send(anyList(), contains("swaps"), contains("hasta las 16:00"));
        assertThat(jornada.getSwapReminderSentFor()).isEqualTo("2026-06-05T16:00");
    }

    @Test
    void nothingDue_sendsNothing() {
        assertThat(serviceAt(LocalDateTime.of(2026, 6, 2, 10, 0)).sendDue(schedule, league)).isFalse();
        verify(pushNotificationPort, never()).send(any(), any(), any());
    }
}
