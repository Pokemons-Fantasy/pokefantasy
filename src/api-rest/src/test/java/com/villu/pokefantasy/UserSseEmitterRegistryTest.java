package com.villu.pokefantasy;

import com.villu.pokefantasy.ports.RealtimeEventPort.Audience;
import com.villu.pokefantasy.ports.RealtimeEventPort.RealtimeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UserSseEmitterRegistryTest {

    private final UserSseEmitterRegistry registry = new UserSseEmitterRegistry();

    @Test
    void userEvent_toClosedConnection_dropsIt() {
        SseEmitter emitter = registry.register("misty");
        emitter.complete();

        registry.onEvent(new RealtimeEvent(Audience.USER, "misty", "steal", "{\"pokemonName\":\"pikachu\"}"));

        assertThat(registry.connectionCount("misty")).isZero();
    }

    @Test
    void leagueEvent_isIgnored() {
        SseEmitter emitter = registry.register("misty");
        emitter.complete();

        registry.onEvent(new RealtimeEvent(Audience.LEAGUE, "misty", "draft-updated", "{}"));

        assertThat(registry.connectionCount("misty")).isEqualTo(1);
    }

    @Test
    void userWithoutConnections_noop() {
        assertThatCode(() -> registry.onEvent(new RealtimeEvent(Audience.USER, "nobody", "steal", "{}")))
                .doesNotThrowAnyException();
        assertThatCode(registry::heartbeat).doesNotThrowAnyException();
    }
}
