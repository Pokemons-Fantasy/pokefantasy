package com.villu.pokefantasy.it;

import com.villu.pokefantasy.redis.RedisRealtimeEventAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
