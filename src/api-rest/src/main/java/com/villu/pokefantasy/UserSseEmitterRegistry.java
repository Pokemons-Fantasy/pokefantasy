package com.villu.pokefantasy;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class UserSseEmitterRegistry {

    // username → emitters activos (puede haber múltiples tabs)
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String username) {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout — heartbeat lo mantiene vivo
        emitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> emitters.computeIfPresent(username, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list; // null removes entry from map atomically
        });
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    public void sendToUser(String username, String eventName, String data) {
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
}
