package com.villu.pokefantasy.it;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Preflight CORS a través de la cadena real de Spring Security (orígenes en {@code SecurityConfig}). */
class CorsIntegrationTest extends IntegrationTest {

    @Test
    void preflightFromTheWeb_isAllowedWithCredentials() throws Exception {
        HttpResponse<String> response = preflight("https://pokefantasy.netlify.app");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("https://pokefantasy.netlify.app");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
    }

    @Test
    void preflightFromADeployPreview_isAllowed() throws Exception {
        HttpResponse<String> response = preflight("https://deploy-preview-89--pokefantasy.netlify.app");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("https://deploy-preview-89--pokefantasy.netlify.app");
    }

    @Test
    void preflightFromAnotherNetlifySite_isRejected() throws Exception {
        HttpResponse<String> response = preflight("https://atacante.netlify.app");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/leagues/my"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void simplePostFromAnotherSite_isRejectedBeforeReachingTheController() throws Exception {
        // Un formulario de otra web (sin preflight) con la sesión de la víctima: Spring lo corta por el Origin.
        // Por eso las cookies pueden seguir con SameSite=None (la app Android llama desde otro sitio).
        for (String origin : new String[] {"https://evil.example", "null"}) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/v1/leagues/invite/no-existe/redeem"))
                    .header("Origin", origin)
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).as(origin).isEqualTo(403);
        }
    }
}
