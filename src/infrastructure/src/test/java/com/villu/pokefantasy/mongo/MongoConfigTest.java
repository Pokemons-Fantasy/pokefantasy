package com.villu.pokefantasy.mongo;

import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    @Test
    void operationsStopWaitingForAStalledSocket() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();

        new MongoConfig().mongoTimeoutsCustomizer().customize(builder);

        // Por defecto el driver espera la respuesta sin límite (readTimeout 0): una conexión colgada
        // dejó un arranque bloqueado hasta que Render canceló el deploy.
        assertThat(builder.build().getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS))
                .isEqualTo(MongoConfig.READ_TIMEOUT.toMillis());
    }
}
