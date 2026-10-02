package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.dto.TypeMember;
import com.villu.pokefantasy.response.PokemonResponseApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PokemonApiAdapterTest {

    private static final String POKE_API_URL = "https://pokeapi.co/api/v2/pokemon?limit=100000&offset=0";

    @Test
    void fetchAllPokemons_succeedsOnFirstAttempt_doesNotRetry() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PokemonApiAdapter adapter = new PokemonApiAdapter(restTemplate);

        server.expect(requestTo(POKE_API_URL))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        PokemonResponseApi result = adapter.fetchAllPokemons();

        assertThat(result).isNotNull();
        assertThat(result.getResults()).isEmpty();
        server.verify();
    }

    @Test
    void fetchAllPokemons_failsTwiceThenSucceeds_retriesAndReturnsResult() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PokemonApiAdapter adapter = new PokemonApiAdapter(restTemplate);

        server.expect(requestTo(POKE_API_URL)).andRespond(withServerError());
        server.expect(requestTo(POKE_API_URL)).andRespond(withServerError());
        server.expect(requestTo(POKE_API_URL))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        PokemonResponseApi result = adapter.fetchAllPokemons();

        assertThat(result).isNotNull();
        server.verify();
    }

    @Test
    void fetchAllPokemons_allAttemptsFail_throwsRestClientExceptionAfterMaxAttempts() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PokemonApiAdapter adapter = new PokemonApiAdapter(restTemplate);

        server.expect(requestTo(POKE_API_URL)).andRespond(withServerError());
        server.expect(requestTo(POKE_API_URL)).andRespond(withServerError());
        server.expect(requestTo(POKE_API_URL)).andRespond(withServerError());

        assertThatThrownBy(adapter::fetchAllPokemons)
                .isInstanceOf(RestClientException.class);

        server.verify();
    }

    @Test
    void fetchPokemonById_allAttemptsFail_wrapsInCheckedExceptionAfterMaxAttempts() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PokemonApiAdapter adapter = new PokemonApiAdapter(restTemplate);

        String url = "https://pokeapi.co/api/v2/pokemon/25/";
        server.expect(requestTo(url)).andRespond(withServerError());
        server.expect(requestTo(url)).andRespond(withServerError());
        server.expect(requestTo(url)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchPokemonById(url, "pikachu"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Failed to fetch data for pokemon: pikachu");

        server.verify();
    }

    @Test
    void fetchPokemonById_succeedsOnFirstAttempt_returnsData() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PokemonApiAdapter adapter = new PokemonApiAdapter(restTemplate);

        String url = "https://pokeapi.co/api/v2/pokemon/25/";
        server.expect(requestTo(url))
                .andRespond(withSuccess("{\"id\":25,\"name\":\"pikachu\"}", MediaType.APPLICATION_JSON));

        var result = adapter.fetchPokemonById(url, "pikachu");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("pikachu");
        server.verify();
    }

    @Test
    void fetchTypeMembers_mapsNameAndSlot() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PokemonApiAdapter adapter = new PokemonApiAdapter(restTemplate);

        server.expect(requestTo("https://pokeapi.co/api/v2/type/poison"))
                .andRespond(withSuccess("""
                        {"id": 4, "name": "poison", "pokemon": [
                          {"pokemon": {"name": "bulbasaur", "url": "u1"}, "slot": 2},
                          {"pokemon": {"name": "ekans", "url": "u2"}, "slot": 1}
                        ]}""", MediaType.APPLICATION_JSON));

        List<TypeMember> members = adapter.fetchTypeMembers("poison");

        assertThat(members).containsExactly(new TypeMember("bulbasaur", 2), new TypeMember("ekans", 1));
        server.verify();
    }

    @Test
    void fetchTypeMembers_emptyBody_returnsEmptyList() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PokemonApiAdapter adapter = new PokemonApiAdapter(restTemplate);

        server.expect(requestTo("https://pokeapi.co/api/v2/type/fire"))
                .andRespond(withSuccess("{\"name\": \"fire\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchTypeMembers("fire")).isEmpty();
    }
}
