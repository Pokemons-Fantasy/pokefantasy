package com.villu.pokefantasy.it;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** CSRF de {@code SecurityConfig}: con cookie de sesión, las escrituras exigen {@code X-Requested-With}. */
class CsrfIntegrationTest extends IntegrationTest {

    private static final String WRITE = "/v1/leagues/invite/no-existe/redeem";

    @Test
    void writeWithSessionCookieAndNoHeader_isRejected() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123").withoutCsrfHeader();

        HttpResponse<String> response = ash.post(WRITE, "");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_REJECTED\"");
        // El rechazo no crea sesión ni deja un token CSRF en el navegador.
        assertThat(response.headers().allValues("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID") || cookie.startsWith("XSRF-TOKEN"));
    }

    @Test
    void writeWithOnlyRefreshCookieAndNoHeader_isRejected() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123").withoutCsrfHeader();
        ash.forgetCookie("jwt");

        HttpResponse<String> response = ash.post(WRITE, "");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_REJECTED\"");
        // El CSRF va antes que JwtAuthFilter: una petición rechazada no renueva la sesión con el refresh.
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void writeWithSessionCookieAndHeader_reachesTheController() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");

        HttpResponse<String> response = ash.post(WRITE, "");

        assertThat(response.statusCode()).isNotEqualTo(403);
        assertThat(response.body()).doesNotContain("CSRF_REJECTED");
    }

    @Test
    void writeWithBearerAndNoCookies_isNotCsrfChecked() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");

        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create(ash.baseUrlForTests() + WRITE))
                .header("Authorization", "Bearer " + ash.cookie("jwt"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isNotIn(401, 403);
        assertThat(response.body()).doesNotContain("CSRF_REJECTED");
    }

    @Test
    void loginAndRegisterWithoutCookiesOrHeader_work() throws Exception {
        ApiClient misty = client().withoutCsrfHeader().loggedInAs("misty_k", "staryu123");

        assertThat(misty.cookie("jwt")).isNotBlank();
    }

    @Test
    void readWithSessionCookieAndNoHeader_works() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123").withoutCsrfHeader();

        assertThat(ash.get("/v1/leagues/my").statusCode()).isEqualTo(200);
    }
}
