package com.villu.pokefantasy;

import com.villu.pokefantasy.ports.RealtimeEventPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class UserSseEmitterRegistry implements RealtimeEventPort.Listener {

    /**
     * Cuánto se rechazan conexiones nuevas de una cuenta borrada: más que la vida del JWT de acceso (15 min), con
     * el que una petición autenticada justo antes del borrado aún podría registrarse (ADR-019).
     */
    static final Duration CLOSED_FOR = Duration.ofMinutes(20);

    // username → emitters activos (puede haber múltiples tabs)
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    // username de cuenta borrada → hasta cuándo no se aceptan conexiones suyas
    private final Map<String, Instant> closedUntil = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public UserSseEmitterRegistry() {
        this(Instant::now);
    }

    UserSseEmitterRegistry(Supplier<Instant> clock) {
        this.clock = clock;
    }

    int connectionCount(String username) {
        List<SseEmitter> list = emitters.get(username);
        return list == null ? 0 : list.size();
    }

    public SseEmitter register(String username) {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout — heartbeat lo mantiene vivo
        Instant closed = closedUntil.get(username);
        if (closed != null) {
            if (clock.get().isBefore(closed)) {
                emitter.complete();
                return emitter;
            }
            closedUntil.remove(username, closed);
        }
        emitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> emitters.computeIfPresent(username, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list; // null removes entry from map atomically
        });
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        sendConnected(emitter);
        return emitter;
    }

    /**
     * Cierra y olvida las conexiones de un usuario (cuenta borrada): si no, sus pestañas abiertas recibirían los
     * eventos de quien registre después ese nombre. Solo las de esta instancia (Render corre una).
     */
    public void closeAll(String username) {
        closedUntil.put(username, clock.get().plus(CLOSED_FOR));
        List<SseEmitter> list = emitters.remove(username);
        if (list != null) {
            list.forEach(SseEmitter::complete);
        }
    }

    /** Entrega local de los eventos de usuario; para emitir uno usa {@link RealtimeNotifier}. */
    @Override
    public void onEvent(RealtimeEventPort.RealtimeEvent event) {
        if (event.audience() == RealtimeEventPort.Audience.USER) {
            sendToUser(event.target(), event.name(), event.data());
        }
    }

    void sendToUser(String username, String eventName, String data) {
        List<SseEmitter> list = emitters.get(username);
        if (list == null || list.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        list.removeAll(dead);
    }

    /** Heartbeat cada 30 s para evitar que el proxy de Render cierre conexiones idle */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.forEach((username, list) -> {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    dead.add(emitter);
                }
            }
            list.removeAll(dead);
        });
    }

    private static void sendConnected(SseEmitter emitter) {
        SseEmitterRegistry.sendConnected(emitter);
    }
}
