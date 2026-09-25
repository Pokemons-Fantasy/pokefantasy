package com.villu.pokefantasy;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void register_overPerUserLimit_dropsOldestConnection() {
        for (int i = 0; i < SseEmitterRegistry.MAX_EMITTERS_PER_USER + 2; i++) {
            registry.register("l1", "ash");
        }
        registry.register("l1", "brock");

        assertThat(registry.connectionCount("l1")).isEqualTo(SseEmitterRegistry.MAX_EMITTERS_PER_USER + 1);
    }

    @Test
    void register_limitIsPerLeague() {
        for (int i = 0; i < SseEmitterRegistry.MAX_EMITTERS_PER_USER; i++) {
            registry.register("l1", "ash");
            registry.register("l2", "ash");
        }

        assertThat(registry.connectionCount("l1")).isEqualTo(SseEmitterRegistry.MAX_EMITTERS_PER_USER);
        assertThat(registry.connectionCount("l2")).isEqualTo(SseEmitterRegistry.MAX_EMITTERS_PER_USER);
    }

    @Test
    void broadcastAndHeartbeat_dropClosedConnections() {
        SseEmitter emitter = registry.register("l1", "ash");
        emitter.complete(); // cerrada por el cliente: el siguiente envío falla

        assertThatCode(() -> registry.broadcastUpdate("l1")).doesNotThrowAnyException();
        assertThat(registry.connectionCount("l1")).isZero();
        assertThatCode(registry::heartbeat).doesNotThrowAnyException();
        assertThatCode(() -> registry.broadcastUpdate("unknown")).doesNotThrowAnyException();
    }
}
