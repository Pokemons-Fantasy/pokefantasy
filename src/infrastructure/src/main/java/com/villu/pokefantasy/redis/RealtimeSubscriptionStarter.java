package com.villu.pokefantasy.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Arranca la suscripción a Redis Pub/Sub en segundo plano y la reintenta hasta que Redis responda, para
 * que la app arranque aunque Redis esté caído (como con PokeAPI). Una vez en marcha, las reconexiones
 * posteriores las gestiona el propio {@link RedisMessageListenerContainer}.
 */
@Component
@Slf4j
public class RealtimeSubscriptionStarter {

    private final RedisMessageListenerContainer container;

    public RealtimeSubscriptionStarter(RedisMessageListenerContainer realtimeEventListenerContainer) {
        this.container = realtimeEventListenerContainer;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${realtime.subscription-retry-ms:10000}")
    public void ensureSubscribed() {
        if (container.isRunning()) {
            return;
        }
        try {
            container.start();
            log.info("Subscribed to realtime events channel {}", RedisRealtimeEventAdapter.CHANNEL);
        } catch (RuntimeException e) {
            // Sin suscripción los eventos SSE no llegan a los clientes; se reintenta en el siguiente ciclo.
            log.warn("Could not subscribe to realtime events (will retry): {}", e.getMessage());
            try {
                container.stop();
            } catch (RuntimeException ignored) {
                // Dejarlo parado para el siguiente intento.
            }
        }
    }
}
