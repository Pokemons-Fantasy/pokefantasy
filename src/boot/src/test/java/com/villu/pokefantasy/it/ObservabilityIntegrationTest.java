package com.villu.pokefantasy.it;

import com.villu.pokefantasy.dto.Role;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Request id en respuestas y errores, y métricas de negocio solo para administradores. */
class ObservabilityIntegrationTest extends IntegrationTest {

    @Test
    void everyResponseHasARequestId_andErrorsIncludeIt() throws Exception {
        HttpResponse<String> ok = client().get("/actuator/health");
        assertThat(ok.headers().firstValue("X-Request-Id")).hasValueSatisfying(id -> assertThat(id).hasSize(36));

        HttpResponse<String> error = client().post("/v1/user", "{\"username\":\"a\",\"password\":\"pikachu123\"}");
        String id = error.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(error.body()).contains("\"requestId\":\"" + id + "\"");
    }

    @Test
    void commandMetrics_onlyForAppAdmins() throws Exception {
        ApiClient ash = client().loggedInAs("ash_k", "pikachu123");
        ash.post("/v1/leagues", "{\"name\":\"Kanto\"}");

        assertThat(ash.get("/actuator/metrics/pokefantasy.commands").statusCode()).isEqualTo(403);
        assertThat(client().get("/actuator/metrics").statusCode()).isIn(401, 403);

        client().loggedInAs("oak", "professor1");
        mongoTemplate.updateFirst(Query.query(Criteria.where("name").is("oak")),
                new Update().set("role", Role.ADMIN), "users");
        ApiClient oak = client();
        oak.post("/v1/user/login", "{\"username\":\"oak\",\"password\":\"professor1\"}");

        HttpResponse<String> metric = oak.get("/actuator/metrics/pokefantasy.commands?tag=command:CreateLeagueCommand");
        assertThat(metric.statusCode()).isEqualTo(200);
        assertThat(metric.body()).contains("\"COUNT\"", "\"outcome\"");
    }
}
