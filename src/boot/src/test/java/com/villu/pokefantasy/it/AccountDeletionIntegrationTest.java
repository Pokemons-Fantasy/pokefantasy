package com.villu.pokefantasy.it;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** DELETE /v1/users/me de punta a punta (spec 2026-10-18-borrado-cuenta-privacidad). */
class AccountDeletionIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "pikachu123";
    private static final String BODY = "{\"password\":\"" + PASSWORD + "\"}";

    private void league(String id, LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId(id);
        league.setName("Liga " + id);
        league.setCreatedBy(members[0].getUsername());
        league.setMembers(new ArrayList<>(List.of(members)));
        league.setSettings(LeagueSettings.defaults());
        mongoTemplate.insert(league);
    }

    private static LeagueMember member(String name, LeagueRole role) {
        return new LeagueMember(name, role, 0);
    }

    @Test
    void deleteAccount_leavesLeaguesWorkingAndAnonymized() throws Exception {
        ApiClient ash = client().loggedInAs("ash", PASSWORD);
        client().loggedInAs("misty", "staryu123");
        client().loggedInAs("brock", "onix12345");
        league("a", member("ash", LeagueRole.ADMIN), member("misty", LeagueRole.USER));
        league("b", member("brock", LeagueRole.ADMIN), member("ash", LeagueRole.USER));
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId("b");
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null))));
        mongoTemplate.insert(draft);
        mongoTemplate.insert(TradeEntity.builder().id("t1").leagueId("b").proposer("ash").responder("brock")
                .proposerPokemonName("pikachu").proposerPokemonId(25).responderPokemonName("onix")
                .responderPokemonId(95).coinsOffered(0).status(TradeStatus.PENDING).createdAt(Instant.now())
                .build());

        HttpResponse<String> response = ash.delete("/v1/users/me", BODY);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.headers().allValues("Set-Cookie")).allMatch(c -> c.contains("Max-Age=0"));
        assertThat(mongoTemplate.findById("a", LeagueEntity.class).getMembers())
                .extracting(LeagueMember::getUsername, LeagueMember::getLeagueRole)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("misty", LeagueRole.ADMIN));
        TradeEntity trade = mongoTemplate.findById("t1", TradeEntity.class);
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.CANCELLED);
        assertThat(trade.getProposer()).isEqualTo("Usuario eliminado");
        DraftEntity after = mongoTemplate.findOne(Query.query(Criteria.where("leagueId").is("b")), DraftEntity.class);
        assertThat(after.getPicks()).extracting(DraftPick::getUsername).containsExactly("brock");
        assertThat(mongoTemplate.findOne(Query.query(Criteria.where("name").is("ash")), UserEntity.class)).isNull();
        assertThat(client().post("/v1/user/login", "{\"username\":\"ash\",\"password\":\"" + PASSWORD + "\"}")
                .statusCode()).isEqualTo(401);
    }

    @Test
    void deleteAccount_onlyMember_deletesTheLeague() throws Exception {
        ApiClient ash = client().loggedInAs("ash", PASSWORD);
        league("solo", member("ash", LeagueRole.ADMIN));

        assertThat(ash.delete("/v1/users/me", BODY).statusCode()).isEqualTo(204);

        assertThat(mongoTemplate.findById("solo", LeagueEntity.class)).isNull();
    }

    @Test
    void oldSessionAfterDeletion_is401() throws Exception {
        ApiClient ash = client().loggedInAs("ash", PASSWORD);
        String oldJwt = ash.cookie("jwt");

        ash.delete("/v1/users/me", BODY);

        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create(ash.baseUrlForTests() + "/v1/leagues/my"))
                .header("Cookie", "jwt=" + oldJwt)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void sameUsernameAfterDeletion_startsEmpty() throws Exception {
        ApiClient ash = client().loggedInAs("ash", PASSWORD);
        client().loggedInAs("misty", "staryu123");
        league("a", member("misty", LeagueRole.ADMIN), member("ash", LeagueRole.USER));
        ash.delete("/v1/users/me", BODY);

        // Mientras viva un JWT de la cuenta borrada (15 min), el nombre no se puede volver a usar.
        HttpResponse<String> tooSoon = client().post("/v1/user", "{\"username\":\"ash\",\"password\":\"otraclave123\"}");
        assertThat(tooSoon.statusCode()).isEqualTo(409);

        redisTemplate.delete("deleted-user:ash"); // como si hubieran pasado los 15 min
        ApiClient newAsh = client().loggedInAs("ash", "otraclave123");

        assertThat(newAsh.get("/v1/leagues/my").body()).isEqualTo("[]");
    }

    @Test
    void wrongPassword_is401AndNothingChanges() throws Exception {
        ApiClient ash = client().loggedInAs("ash", PASSWORD);

        HttpResponse<String> response = ash.delete("/v1/users/me", "{\"password\":\"incorrecta\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"INVALID_CREDENTIALS\"");
        assertThat(ash.get("/v1/leagues/my").statusCode()).isEqualTo(200);
    }
}
