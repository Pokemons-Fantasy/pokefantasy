package com.villu.pokefantasy.it;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.ports.ShowdownUser;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Vinculación de la cuenta de Showdown de punta a punta: índice único sparse, transacción y errores. */
class ShowdownLinkIntegrationTest extends IntegrationTest {

    @Test
    void linkUnlinkRelink() throws Exception {
        when(showdownApiAdapter.findUser("ashketchum"))
                .thenReturn(Optional.of(new ShowdownUser("Ash Ketchum", "ashketchum")));
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        ApiClient misty = client().loggedInAs("misty", "starmie123");
        ApiClient brock = client().loggedInAs("brock", "onix12345");

        assertThat(ash.put("/v1/user/showdown", "{\"name\":\"ash ketchum\"}").statusCode()).isEqualTo(204);
        assertThat(ash.get("/v1/user/me").body()).contains("\"showdownName\":\"Ash Ketchum\"");

        HttpResponse<String> taken = misty.put("/v1/user/showdown", "{\"name\":\"Ash Ketchum\"}");
        assertThat(taken.statusCode()).isEqualTo(409);
        assertThat(taken.body()).contains("CONFLICT").contains("ya está vinculada a otro jugador");

        // Dos usuarios sin cuenta (uno que la quitó y otro que nunca la tuvo) no chocan en el índice único.
        assertThat(ash.delete("/v1/user/showdown").statusCode()).isEqualTo(204);
        assertThat(brock.delete("/v1/user/showdown").statusCode()).isEqualTo(204);
        assertThat(misty.put("/v1/user/showdown", "{\"name\":\"Ash Ketchum\"}").statusCode()).isEqualTo(204);
        assertThat(ash.get("/v1/user/me").body()).doesNotContain("Ash Ketchum");
    }

    @Test
    void showdownDown_returns502() throws Exception {
        when(showdownApiAdapter.findUser("ashketchum"))
                .thenThrow(new ShowdownUnavailableException("Showdown no responde, inténtalo en un rato", null));
        ApiClient ash = client().loggedInAs("ash", "pikachu123");

        HttpResponse<String> response = ash.put("/v1/user/showdown", "{\"name\":\"Ash Ketchum\"}");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).contains("SHOWDOWN_UNAVAILABLE");
    }
}
