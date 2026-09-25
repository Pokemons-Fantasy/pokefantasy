package com.villu.pokefantasy;

import com.villu.pokefantasy.ports.RealtimeEventPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@Component
public class SseEmitterRegistry implements RealtimeEventPort.Listener {

    /**
     * Conexiones abiertas por usuario y liga (varias pestañas o dispositivos). Al superar el límite se
     * cierra la más antigua: así un cliente que reconecta en bucle no acumula conexiones sin fin.
     */
    static final int MAX_EMITTERS_PER_USER = 3;

    private record Subscription(String username, SseEmitter emitter) {}

    // leagueId → suscripciones activas
    private final Map<String, List<Subscription>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String leagueId, String username) {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout — heartbeat lo mantiene vivo
        Subscription subscription = new Subscription(username, emitter);
        List<Subscription> list = emitters.computeIfAbsent(leagueId, k -> new CopyOnWriteArrayList<>());

        synchronized (list) {
            List<Subscription> own = list.stream().filter(s -> s.username().equals(username)).toList();
            for (int i = 0; i <= own.size() - MAX_EMITTERS_PER_USER; i++) {
                list.remove(own.get(i));
                own.get(i).emitter().complete();
            }
            list.add(subscription);
        }

        Runnable cleanup = () -> list.remove(subscription);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        sendConnected(emitter);
        return emitter;
    }

    int connectionCount(String leagueId) {
        return emitters.getOrDefault(leagueId, Collections.emptyList()).size();
    }

    /** Entrega local de los eventos de liga; para emitir uno usa {@link RealtimeNotifier}. */
    @Override
    public void onEvent(RealtimeEventPort.RealtimeEvent event) {
        if (event.audience() != RealtimeEventPort.Audience.LEAGUE) return;
        send(emitters.getOrDefault(event.target(), Collections.emptyList()),
                () -> SseEmitter.event().name(event.name()).data(event.data()));
    }

    /** Heartbeat cada 30 s para evitar que el proxy de Render cierre conexiones idle */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.values().forEach(list -> send(list, () -> SseEmitter.event().comment("ping")));
    }

    // Un builder por envío: SseEventBuilder.build() muta su estado y no se puede reutilizar.
    private static void send(List<Subscription> list, Supplier<SseEmitter.SseEventBuilder> event) {
        List<Subscription> dead = new ArrayList<>();
        for (Subscription subscription : list) {
            try {
                subscription.emitter().send(event.get());
            } catch (IOException | IllegalStateException e) {
                dead.add(subscription);
            }
        }
        list.removeAll(dead);
    }

    /**
     * Primer mensaje (un comentario, que EventSource ignora): obliga a enviar ya las cabeceras, así el
     * cliente ve la conexión abierta al momento y no al primer heartbeat (hasta 30 s después).
     */
    static void sendConnected(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
