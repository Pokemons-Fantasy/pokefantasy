package com.villu.pokefantasy;

import com.villu.pokefantasy.ports.RealtimeEventPort;
import com.villu.pokefantasy.ports.RealtimeEventPort.Audience;
import com.villu.pokefantasy.ports.RealtimeEventPort.RealtimeEvent;
import org.springframework.stereotype.Component;

/**
 * Punto único para emitir eventos SSE. Publica en {@link RealtimeEventPort}, que los hace llegar a todas
 * las instancias; cada una los entrega a sus conexiones ({@link SseEmitterRegistry},
 * {@link UserSseEmitterRegistry}).
 */
@Component
public class RealtimeNotifier {

    static final String DRAFT_UPDATED = "draft-updated";

    private final RealtimeEventPort realtimeEventPort;

    public RealtimeNotifier(RealtimeEventPort realtimeEventPort) {
        this.realtimeEventPort = realtimeEventPort;
    }

    /** Avisa a quien siga el draft de la liga de que ha cambiado (el cliente vuelve a pedir el estado). */
    public void draftUpdated(String leagueId) {
        realtimeEventPort.publish(new RealtimeEvent(Audience.LEAGUE, leagueId, DRAFT_UPDATED, "{}"));
    }

    /** Notificación personal (robo, trade propuesto…) a todas las conexiones del usuario. */
    public void notifyUser(String username, String eventName, String data) {
        realtimeEventPort.publish(new RealtimeEvent(Audience.USER, username, eventName, data));
    }
}
