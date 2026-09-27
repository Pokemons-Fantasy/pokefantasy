package com.villu.pokefantasy.it;

import com.villu.pokefantasy.redis.RedisRealtimeEventAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tiempo real de punta a punta: una conexión SSE real recibe el evento del draft, que viaja por Redis
 * Pub/Sub (el mismo camino que usaría otra instancia).
 */
@ExtendWith(OutputCaptureExtension.class)
class RealtimeIntegrationTest extends IntegrationTest {

    @Autowired private RedisMessageListenerContainer realtimeEventListenerContainer;

    @Test
    void draftChange_reachesOpenSseConnectionThroughRedis() throws Exception {
        await().atMost(Duration.ofSeconds(20)).until(realtimeEventListenerContainer::isRunning);
        await().atMost(Duration.ofSeconds(10)).until(() -> subscribers() >= 1);

        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");
        String leagueId = ash.post("/v1/leagues", "{\"name\":\"Liga SSE\"}").body().replace("\"", "");

        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        CompletableFuture<HttpResponse<Stream<String>>> stream = HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/leagues/" + leagueId + "/draft/events"))
                        .header("Cookie", "jwt=" + ash.cookie("jwt"))
                        .header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofLines());
        HttpResponse<Stream<String>> response = stream.get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        Thread reader = Thread.ofVirtual().start(() -> response.body().forEach(lines::add));
        try {
            assertThat(ash.post("/v1/leagues/" + leagueId + "/draft/start", "{\"turnOrder\":[\"ash_k\"]}")
                    .statusCode()).isEqualTo(200);

            String line;
            do {
                line = lines.poll(10, TimeUnit.SECONDS);
            } while (line != null && !line.startsWith("event:"));
            assertThat(line).isEqualTo("event:draft-updated");
        } finally {
            reader.interrupt();
            stream.cancel(true);
        }
    }

    /**
     * Cerrar una conexión SSE desde el servidor (aquí, la más antigua al superar el límite por usuario)
     * provoca un dispatch ASYNC que vuelve a pasar por la cadena de seguridad. Si se autoriza sin la
     * autenticación de la petición original, Spring Security lo rechaza con la respuesta ya enviada y
     * Tomcat lo registra como ERROR (que acaba en Sentry).
     */
    @Test
    void closingAnSseConnectionFromTheServer_isNotRejectedBySecurity(CapturedOutput output) throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");
        String leagueId = ash.post("/v1/leagues", "{\"name\":\"Liga SSE\"}").body().replace("\"", "");

        List<HttpResponse<Stream<String>>> streams = new ArrayList<>();
        try {
            // Una más que SseEmitterRegistry.MAX_EMITTERS_PER_USER (3): el registro cierra la más antigua.
            for (int i = 0; i < 4; i++) {
                streams.add(openDraftEvents(ash, leagueId));
            }
            HttpResponse<Stream<String>> evicted = streams.getFirst();
            Throwable closeError = CompletableFuture.runAsync(() -> evicted.body().forEach(line -> { }))
                    .handle((ok, error) -> error)
                    .get(10, TimeUnit.SECONDS);

            assertThat(output.getAll())
                    .doesNotContain("AuthorizationDeniedException")
                    .doesNotContain("response is already committed");
            // Con el rechazo, Tomcat corta la conexión a mitad de un chunk en vez de cerrarla limpia.
            assertThat(closeError).as("la conexión expulsada se cierra limpia").isNull();
        } finally {
            streams.forEach(s -> s.body().close());
        }
    }

    private HttpResponse<Stream<String>> openDraftEvents(ApiClient user, String leagueId) throws Exception {
        HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/leagues/" + leagueId + "/draft/events"))
                        .header("Cookie", "jwt=" + user.cookie("jwt"))
                        .header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    @Test
    void draftEvents_requireLeagueMembership() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");
        String leagueId = ash.post("/v1/leagues", "{\"name\":\"Liga privada\"}").body().replace("\"", "");
        ApiClient gary = client().loggedInAs("gary_o", "eevee1234");

        assertThat(gary.get("/v1/leagues/" + leagueId + "/draft/events").statusCode()).isEqualTo(403);
        assertThat(client().get("/v1/leagues/" + leagueId + "/draft/events").statusCode()).isIn(401, 403);
    }

    /** {@code PUBSUB NUMSUB} del canal de eventos: cuántas instancias están suscritas. */
    private long subscribers() throws Exception {
        String[] reply = REDIS.execInContainer("redis-cli", "PUBSUB", "NUMSUB", RedisRealtimeEventAdapter.CHANNEL)
                .getStdout().trim().split("\\s+");
        return reply.length == 2 ? Long.parseLong(reply[1]) : 0L;
    }
}
