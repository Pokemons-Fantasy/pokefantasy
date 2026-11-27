package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.league.LeaguePushTokens;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import org.springframework.stereotype.Service;

/**
 * Aviso por push a toda la liga cuando una jornada tiene ya todos sus resultados (se abre el mercado de la
 * siguiente o termina la temporada). Una vez por jornada: la marca {@code completionNotified}, que guarda
 * quien llama. Lo usan el registro del admin y el import de replays, no corregir ni deshacer.
 */
@Service
public class JornadaCompletionNotifier {

    private final LeaguePushTokens leaguePushTokens;
    private final PushNotificationPort pushNotificationPort;

    public JornadaCompletionNotifier(LeaguePushTokens leaguePushTokens, PushNotificationPort pushNotificationPort) {
        this.leaguePushTokens = leaguePushTokens;
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

        pushNotificationPort.send(leaguePushTokens.ofMembers(league), PushMessage.teams(league.getId(), "Jornada " + roundNumber + " completa", body));
        jornada.setCompletionNotified(true);
    }
}
