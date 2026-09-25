package com.villu.pokefantasy.it;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Sesión real: cookies, renovación transparente con el refresh token, logout y errores ProblemDetail. */
class SessionIntegrationTest extends IntegrationTest {

    @Test
    void login_setsBothCookies_andProtectedEndpointsWork() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");

        assertThat(ash.cookie("jwt")).isNotBlank();
        assertThat(ash.cookie("refresh")).isNotBlank();
        assertThat(ash.get("/v1/leagues/my").statusCode()).isEqualTo(200);
    }

    @Test
    void withoutSession_protectedEndpointsAreRejected() throws Exception {
        assertThat(client().get("/v1/leagues/my").statusCode()).isIn(401, 403);
    }

    @Test
    void expiredAccessToken_isRenewedTransparentlyFromRefreshCookie() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");
        String oldRefresh = ash.cookie("refresh");
        ash.forgetCookie("jwt"); // como si el navegador la hubiera descartado al caducar

        HttpResponse<String> response = ash.get("/v1/leagues/my");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(ash.cookie("jwt")).isNotBlank();
        assertThat(ash.cookie("refresh")).isEqualTo(oldRefresh);
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");
        String refresh = ash.cookie("refresh");

        assertThat(ash.post("/v1/user/logout", "").statusCode()).isEqualTo(200);
        assertThat(ash.cookie("jwt")).isNull();
        assertThat(ash.cookie("refresh")).isNull();

        // Aunque alguien conservara la cookie, ya no vale en el servidor.
        HttpResponse<String> reused = sendWithRefresh(client(), refresh);
        assertThat(reused.statusCode()).isIn(401, 403);
    }

    @Test
    void errors_areProblemDetailWithStableCode() throws Exception {
        HttpResponse<String> badRegister = client().post("/v1/user", "{\"username\":\"a\",\"password\":\"pikachu123\"}");
        assertThat(badRegister.statusCode()).isEqualTo(400);
        assertThat(badRegister.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(badRegister.body()).contains("\"code\":\"BAD_REQUEST\"");

        HttpResponse<String> wrongMethod = client().put("/v1/user/login", "{}");
        assertThat(wrongMethod.statusCode()).isEqualTo(405);
        assertThat(wrongMethod.body()).contains("\"code\":\"METHOD_NOT_ALLOWED\"");
    }

    @Test
    void failedLogins_areRateLimitedPerUser() throws Exception {
        client().loggedInAs("ash_k", "pikachu123");
        ApiClient attacker = client();
        String wrong = "{\"username\":\"ash_k\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 5; i++) {
            assertThat(attacker.post("/v1/user/login", wrong).statusCode()).isEqualTo(401);
        }

        HttpResponse<String> blocked = attacker.post("/v1/user/login",
                "{\"username\":\"ash_k\",\"password\":\"pikachu123\"}");

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
    }

    private static HttpResponse<String> sendWithRefresh(ApiClient client, String refresh) throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        return http.send(java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(client.baseUrlForTests() + "/v1/leagues/my"))
                .header("Cookie", "refresh=" + refresh).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
