package com.villu.pokefantasy.adapters;

import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.ports.ShowdownPort;
import com.villu.pokefantasy.ports.ShowdownReplay;
import com.villu.pokefantasy.ports.ShowdownReplaySummary;
import com.villu.pokefantasy.ports.ShowdownUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** API pública de Pokémon Showdown. Sin reintentos: quien llama es una petición del usuario. */
@Component
@Slf4j
public class ShowdownApiAdapter implements ShowdownPort {

    private static final String USER_URL = "https://pokemonshowdown.com/users/{id}.json";
    private static final String PASTE_URL = "https://pokepast.es/{id}/json";
    private static final String SEARCH_URL = "https://replay.pokemonshowdown.com/search.json?user={u1}&user2={u2}";
    private static final String REPLAY_URL = "https://replay.pokemonshowdown.com/{id}.json";
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

    @Override
    public Optional<String> getPokepaste(String pasteId) {
        try {
            PasteJson paste = restTemplate.getForObject(PASTE_URL, PasteJson.class, pasteId);
            return Optional.ofNullable(paste).map(PasteJson::paste);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("pokepast.es no responde al leer el paste {}", pasteId, e);
            throw new ShowdownUnavailableException(UNAVAILABLE, e);
        }
    }

    @Override
    public List<ShowdownReplaySummary> searchReplays(String userId1, String userId2) {
        try {
            SummaryJson[] found = restTemplate.getForObject(SEARCH_URL, SummaryJson[].class, userId1, userId2);
            return found == null ? List.of() : Arrays.stream(found)
                    .map(s -> new ShowdownReplaySummary(s.id(), s.players(), s.uploadtime()))
                    .toList();
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        } catch (RestClientException e) {
            log.warn("Showdown no responde al buscar replays de {} y {}", userId1, userId2, e);
            throw new ShowdownUnavailableException(UNAVAILABLE, e);
        }
    }

    @Override
    public Optional<ShowdownReplay> getReplay(String replayId) {
        try {
            ReplayJson r = restTemplate.getForObject(REPLAY_URL, ReplayJson.class, replayId);
            return Optional.ofNullable(r)
                    .map(x -> new ShowdownReplay(x.id(), x.formatid(), x.players(), x.log(), x.uploadtime()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Showdown no responde al leer el replay {}", replayId, e);
            throw new ShowdownUnavailableException(UNAVAILABLE, e);
        }
    }

    /** Un resultado de {@code search.json} (se ignoran {@code format}, {@code private} y {@code rating}). */
    private record SummaryJson(String id, List<String> players, long uploadtime) {}

    /** {@code /<id>.json} de un replay. */
    private record ReplayJson(String id, String formatid, List<String> players, String log, long uploadtime) {}

    /** {@code /{id}/json} de pokepast.es: {@code paste}, {@code title}, {@code author}, {@code notes}. */
    private record PasteJson(String paste) {}

    /** Solo los campos que se usan de {@code /users/{id}.json}; Jackson ignora el resto. */
    private record UserJson(String username, String userid) {}
}
