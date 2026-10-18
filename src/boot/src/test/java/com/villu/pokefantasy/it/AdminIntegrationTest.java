package com.villu.pokefantasy.it;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Panel de moderación (L5): /v1/admin/** solo para el rol global ADMIN. */
class AdminIntegrationTest extends IntegrationTest {

    private ApiClient admin(String name) throws Exception {
        client().loggedInAs(name, "rootroot1");
        mongoTemplate.updateFirst(Query.query(Criteria.where("name").is(name)), Update.update("role", Role.ADMIN),
                UserEntity.class);
        return client().loggedInAs(name, "rootroot1");
    }

    private void league(String id, String name, LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId(id);
        league.setName(name);
        league.setCreatedBy(members[0].getUsername());
        league.setMembers(new ArrayList<>(List.of(members)));
        league.setSettings(LeagueSettings.defaults());
        mongoTemplate.insert(league);
    }

    private UserEntity user(String name) {
        return mongoTemplate.findOne(Query.query(Criteria.where("name").is(name)), UserEntity.class);
    }

    @Test
    void user_gets403OnEveryAdminEndpoint() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        client().loggedInAs("troll", "trolltroll1");
        league("l1", "Insulto", new LeagueMember("troll", LeagueRole.ADMIN, 0));

        assertThat(List.of(
                ash.get("/v1/admin/users?q=tr"),
                ash.get("/v1/admin/leagues?q=in"),
                ash.delete("/v1/admin/users/troll/avatar"),
                ash.put("/v1/admin/leagues/l1/name", "{\"name\":\"Kanto\"}"),
                ash.delete("/v1/admin/users/troll")))
                .extracting(HttpResponse::statusCode)
                .containsOnly(403);
        assertThat(mongoTemplate.findById("l1", LeagueEntity.class).getName()).isEqualTo("Insulto");
        assertThat(user("troll")).isNotNull();
    }

    @Test
    void admin_searchesRemovesPhotoRenamesAndDeletes() throws Exception {
        ApiClient root = admin("root");
        client().loggedInAs("troll", "trolltroll1");
        client().loggedInAs("misty", "staryu123");
        mongoTemplate.updateFirst(Query.query(Criteria.where("name").is("troll")), Update.update("avatarVersion", 5L),
                UserEntity.class);
        league("l1", "Insulto", new LeagueMember("troll", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0));

        assertThat(root.get("/v1/admin/users?q=tro").body()).contains("\"username\":\"troll\"");
        assertThat(root.get("/v1/admin/leagues?q=insu").body()).contains("\"id\":\"l1\"").contains("\"members\":2");

        assertThat(root.delete("/v1/admin/users/troll/avatar").statusCode()).isEqualTo(204);
        assertThat(user("troll").getAvatarVersion()).isNull();

        assertThat(root.put("/v1/admin/leagues/l1/name", "{\"name\":\"  Kanto  \"}").statusCode()).isEqualTo(204);
        assertThat(mongoTemplate.findById("l1", LeagueEntity.class).getName()).isEqualTo("Kanto");
        assertThat(root.put("/v1/admin/leagues/l1/name", "{\"name\":\"" + "x".repeat(41) + "\"}").statusCode())
                .isEqualTo(400);

        assertThat(root.delete("/v1/admin/users/troll").statusCode()).isEqualTo(204);
        assertThat(user("troll")).isNull();
        assertThat(mongoTemplate.findById("l1", LeagueEntity.class).getMembers())
                .extracting(LeagueMember::getUsername, LeagueMember::getLeagueRole)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("misty", LeagueRole.ADMIN));
    }

    @Test
    void admin_cannotDeleteAnotherAdmin() throws Exception {
        ApiClient root = admin("root");
        admin("boss");

        HttpResponse<String> response = root.delete("/v1/admin/users/boss");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(user("boss")).isNotNull();
    }

    @Test
    void me_reportsAdmin() throws Exception {
        assertThat(admin("root").get("/v1/user/me").body()).contains("\"admin\":true");
        assertThat(client().loggedInAs("ash", "pikachu123").get("/v1/user/me").body()).contains("\"admin\":false");
    }
}
