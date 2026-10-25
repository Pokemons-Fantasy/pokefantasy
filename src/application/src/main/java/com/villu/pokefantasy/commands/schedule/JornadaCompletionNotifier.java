package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aviso por push a toda la liga cuando una jornada tiene ya todos sus resultados (se abre el mercado de la
 * siguiente o termina la temporada). Una vez por jornada: la marca {@code completionNotified}, que guarda
 * quien llama. Lo usan el registro del admin y el import de replays, no corregir ni deshacer.
 */
@Service
public class JornadaCompletionNotifier {

    private final UserRepository userRepository;
    private final PushNotificationPort pushNotificationPort;

    public JornadaCompletionNotifier(UserRepository userRepository, PushNotificationPort pushNotificationPort) {
        this.userRepository = userRepository;
        this.pushNotificationPort = pushNotificationPort;
    }

    /** Si la jornada {@code roundNumber} acaba de completarse y no se avisó, avisa y la marca (sin guardar). */
    public void notifyIfCompleted(LeagueEntity league, ScheduleEntity schedule, int roundNumber) {
        Jornada jornada = schedule.getJornadas().stream()
                .filter(j -> j.getRoundNumber() == roundNumber).findFirst().orElse(null);
        if (jornada == null || Boolean.TRUE.equals(jornada.getCompletionNotified())
                || !jornada.getMatches().stream().allMatch(m -> m.getStatus() == MatchStatus.COMPLETED)) {
            return;
        }
        boolean last = schedule.getJornadas().stream().noneMatch(j -> j.getRoundNumber() > roundNumber);
        String body = last ? "Ya están todos los resultados: fin de la temporada."
                : "Ya están todos los resultados. Se abre el mercado de la jornada " + (roundNumber + 1) + ".";

        List<String> usernames = league.getMembers().stream().map(LeagueMember::getUsername).toList();
        Map<String, UserEntity> users = userRepository.findByUsernames(usernames).stream()
                .collect(Collectors.toMap(UserEntity::getName, Function.identity(), (a, b) -> a));
        List<String> tokens = usernames.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .map(UserEntity::getFcmTokens)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
        pushNotificationPort.send(tokens, PushMessage.teams(league.getId(), "Jornada " + roundNumber + " completa", body));
        jornada.setCompletionNotified(true);
    }
}
