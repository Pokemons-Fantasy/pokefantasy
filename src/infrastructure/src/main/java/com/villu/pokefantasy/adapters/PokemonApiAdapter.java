package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.ports.PokemonApiPort;
import com.villu.pokefantasy.response.PokemonResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.function.Supplier;

@Component
@Slf4j
public class PokemonApiAdapter implements PokemonApiPort {

    private static final String POKE_API_URL = "https://pokeapi.co/api/v2/pokemon?limit=100000&offset=0";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private final RestTemplate restTemplate;

    public PokemonApiAdapter() {
        this(buildRestTemplate());
    }

    /** Visible al paquete para inyectar un RestTemplate de test (p.ej. con MockRestServiceServer). */
    PokemonApiAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    @Override
    public PokemonResponseApi fetchAllPokemons() {
        log.info("Get Pokemons API URL: {}", POKE_API_URL);
        return fetchWithRetry(() -> restTemplate.getForObject(POKE_API_URL, PokemonResponseApi.class), "all pokemons");
    }

    @Override
    public Pokemons fetchPokemonById(String url, String name) throws Exception {
        try {
            return fetchWithRetry(() -> restTemplate.getForObject(url, Pokemons.class), name);
        } catch (Exception e) {
            log.error("Error fetching data for pokemon: {}", name, e);
            throw new Exception("Failed to fetch data for pokemon: " + name, e);
        }
    }

    @Override
    public Pokemons fetchPokemonData(String url) {
        return null;
    }

    /**
     * Reintenta hasta MAX_ATTEMPTS veces con espera fija entre intentos. Si todos fallan,
     * relanza la última excepción — el arranque sigue abortando si PokeAPI no responde,
     * pero ahora con timeout acotado en vez de colgarse indefinidamente.
     */
    private <T> T fetchWithRetry(Supplier<T> call, String context) {
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RestClientException e) {
                lastError = e;
                log.warn("PokeAPI call failed for '{}' (attempt {}/{}): {}", context, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_DELAY);
                }
            }
        }
        throw lastError;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
