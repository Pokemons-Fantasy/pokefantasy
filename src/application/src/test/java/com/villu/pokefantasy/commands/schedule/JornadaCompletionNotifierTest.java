package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.PushMessage;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JornadaCompletionNotifierTest {

    @Mock private UserRepository userRepository;
    @Mock private PushNotificationPort pushNotificationPort;

    private Match last;
    private Jornada third;
    private ScheduleEntity schedule;
    private LeagueEntity league;
    private JornadaCompletionNotifier notifier;

    private static UserEntity user(String name, List<String> tokens) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setFcmTokens(tokens);
        return u;
    }

    @BeforeEach
    void setUp() {
        last = new Match("m2", "brock", "gary", "brock", MatchStatus.COMPLETED);
        third = new Jornada(3, new ArrayList<>(List.of(
                new Match("m1", "ash", "misty", "ash", MatchStatus.COMPLETED), last)), null);
        Jornada fourth = new Jornada(4, new ArrayList<>(List.of(new Match("m3", "ash", "brock", null, MatchStatus.PENDING))), null);
        schedule = new ScheduleEntity();
        schedule.setJornadas(new ArrayList<>(List.of(third, fourth)));

        league = new LeagueEntity();
        league.setId("l1");
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0), new LeagueMember("misty", LeagueRole.USER, 0),
                new LeagueMember("brock", LeagueRole.USER, 0), new LeagueMember("gone", LeagueRole.USER, 0)));
        // "gone" no tiene usuario; misty no tiene tokens.
        lenient().when(userRepository.findByUsername("ash")).thenReturn(user("ash", List.of("t-ash")));
        lenient().when(userRepository.findByUsername("misty")).thenReturn(user("misty", null));
        lenient().when(userRepository.findByUsername("brock")).thenReturn(user("brock", List.of("t-brock1", "t-brock2")));

        notifier = new JornadaCompletionNotifier(userRepository, pushNotificationPort);
    }

    @Test
    void lastResultOfJornada_pushesEveryMemberWithTokens_once() {
        notifier.notifyIfCompleted(league, schedule, 3);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationPort).send(eq(List.of("t-ash", "t-brock1", "t-brock2")), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Jornada 3 completa");
        assertThat(message.getValue().body())
                .isEqualTo("Ya están todos los resultados. Se abre el mercado de la jornada 4.");
        assertThat(message.getValue().path()).isEqualTo("/leagues/l1/teams");
        assertThat(third.getCompletionNotified()).isTrue();
    }

    @Test
    void lastJornada_endOfSeason() {
        schedule.setJornadas(new ArrayList<>(List.of(third)));

        notifier.notifyIfCompleted(league, schedule, 3);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushNotificationPort).send(any(), message.capture());
        assertThat(message.getValue().body()).isEqualTo("Ya están todos los resultados: fin de la temporada.");
    }

    @Test
    void matchesLeft_nothing() {
        last.setStatus(MatchStatus.PENDING);

        notifier.notifyIfCompleted(league, schedule, 3);

        verify(pushNotificationPort, never()).send(any(), any());
        assertThat(third.getCompletionNotified()).isNull();
    }

    @Test
    void alreadyNotified_nothing() {
        third.setCompletionNotified(true);

        notifier.notifyIfCompleted(league, schedule, 3);

        verify(pushNotificationPort, never()).send(any(), any());
    }
}
