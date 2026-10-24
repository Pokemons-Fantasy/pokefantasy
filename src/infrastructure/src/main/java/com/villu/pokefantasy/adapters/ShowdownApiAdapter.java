package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.ports.ShowdownPort;
import com.villu.pokefantasy.ports.ShowdownUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

/** API pública de Pokémon Showdown. Sin reintentos: quien llama es una petición del usuario. */
@Component
@Slf4j
public class ShowdownApiAdapter implements ShowdownPort {

    private static final String USER_URL = "https://pokemonshowdown.com/users/{id}.json";
    static final String UNAVAILABLE = "Showdown no responde, inténtalo en un rato";

    private final RestTemplate restTemplate;

    public ShowdownApiAdapter() {
        this(buildRestTemplate());
    }

    /** Visible al paquete para inyectar un RestTemplate de test (p.ej. con MockRestServiceServer). */
    ShowdownApiAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    @Override
    public Optional<ShowdownUser> findUser(String showdownId) {
        try {
            UserJson user = restTemplate.getForObject(USER_URL, UserJson.class, showdownId);
            // Sin userid no hay cuenta: guardarlo como null rompería el índice único de showdownId.
            return Optional.ofNullable(user)
                    .filter(u -> u.userid() != null && !u.userid().isBlank())
                    .map(u -> new ShowdownUser(u.username(), u.userid()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Showdown no responde al buscar el usuario {}", showdownId, e);
            throw new ShowdownUnavailableException(UNAVAILABLE, e);
        }
    }

    /** Solo los campos que se usan de {@code /users/{id}.json}; Jackson ignora el resto. */
    private record UserJson(String username, String userid) {}
}
