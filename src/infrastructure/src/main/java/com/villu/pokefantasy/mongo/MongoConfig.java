package com.villu.pokefantasy.mongo;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfig {

    /**
     * Por defecto el driver espera la respuesta de Mongo sin límite. Si la conexión se queda colgada sin
     * cerrarse, la operación no vuelve nunca: el 25-09 una migración de arranque bloqueó el hilo principal
     * hasta que Render canceló el deploy (15 min sin abrir el puerto). Con límite falla en segundos y el
     * driver reintenta una vez las lecturas y escrituras reintentables.
     */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimeoutsCustomizer() {
        return builder -> builder.applyToSocketSettings(
                socket -> socket.readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
    }
}
