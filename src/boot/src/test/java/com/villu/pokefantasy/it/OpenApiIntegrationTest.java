package com.villu.pokefantasy.it;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La especificación OpenAPI se genera y es pública. Se guarda en {@code boot/target/openapi.json}
 * (la CI la publica como artefacto) para generar el cliente TypeScript del frontend, p. ej. con
 * {@code npx openapi-typescript openapi.json -o src/api/schema.d.ts}.
 */
class OpenApiIntegrationTest extends IntegrationTest {

    @Test
    void specIsPublicAndDescribesTheApi() throws Exception {
        HttpResponse<String> spec = client().get("/v3/api-docs");

        assertThat(spec.statusCode()).isEqualTo(200);
        assertThat(spec.body()).contains(
                "\"title\":\"PokeFantasy API\"",
                "\"/v1/user/login\"",
                "\"/v1/leagues/my\"",
                "\"/v1/leagues/{leagueId}/draft/pick\"",
                "\"/v1/leagues/{leagueId}/schedule/matches/{matchId}/result\"",
                "\"cookie\"");
        Files.writeString(Path.of("target", "openapi.json"), spec.body());
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        HttpResponse<String> ui = client().get("/swagger-ui/index.html");

        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).containsIgnoringCase("swagger");
    }
}
