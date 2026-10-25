package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.ports.ShowdownUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShowdownApiAdapterTest {

    private static final String USER_URL = "https://pokemonshowdown.com/users/ashketchum.json";

    private MockRestServiceServer server;
    private ShowdownApiAdapter adapter;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        adapter = new ShowdownApiAdapter(restTemplate);
    }

    @Test
    void findUser_existing_returnsCanonicalNameAndId() {
        server.expect(requestTo(USER_URL)).andRespond(withSuccess(
                "{\"username\":\"Ash Ketchum\",\"userid\":\"ashketchum\",\"registertime\":1,\"group\":1,\"ratings\":{}}",
                MediaType.APPLICATION_JSON));

        Optional<ShowdownUser> result = adapter.findUser("ashketchum");

        assertThat(result).contains(new ShowdownUser("Ash Ketchum", "ashketchum"));
        server.verify();
    }

    @Test
    void findUser_notFound_returnsEmpty() {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.findUser("ashketchum")).isEmpty();
    }

    @Test
    void findUser_okWithoutUserid_returnsEmpty() {
        // Un userid null o vacío acabaría guardado como null en el índice único (dos usuarios chocarían).
        server.expect(requestTo(USER_URL)).andRespond(withSuccess(
                "{\"username\":\"Ash Ketchum\",\"userid\":\"\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.findUser("ashketchum")).isEmpty();
    }

    private static final String PASTE_URL = "https://pokepast.es/abc123/json";

    @Test
    void getPokepaste_returnsPasteText() {
        server.expect(requestTo(PASTE_URL)).andRespond(withSuccess(
                "{\"paste\":\"Pikachu @ Light Ball\\r\\n\",\"title\":\"t\",\"author\":\"a\",\"notes\":\"\"}",
                MediaType.APPLICATION_JSON));

        assertThat(adapter.getPokepaste("abc123")).contains("Pikachu @ Light Ball\r\n");
    }

    @Test
    void getPokepaste_notFound_empty() {
        server.expect(requestTo(PASTE_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.getPokepaste("abc123")).isEmpty();
    }

    @Test
    void getPokepaste_serverError_throwsUnavailable() {
        server.expect(requestTo(PASTE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.getPokepaste("abc123")).isInstanceOf(ShowdownUnavailableException.class);
    }

    @Test
    void findUser_serverError_throwsUnavailable() {
        server.expect(requestTo(USER_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.findUser("ashketchum"))
                .isInstanceOf(ShowdownUnavailableException.class)
                .hasMessage("Showdown no responde, inténtalo en un rato");
    }
}
