package com.villu.pokefantasy.it;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.ports.ShowdownReplay;
import com.villu.pokefantasy.ports.ShowdownReplaySummary;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Import de una serie Bo3 de punta a punta: candidatos, registro (monedas, marcador, replays) y no reimportar. */
class ReplayImportIntegrationTest extends IntegrationTest {

    private static final String LEAGUE = "league-import";
    private static final String BASE = "/v1/leagues/" + LEAGUE + "/schedule";

    private static ShowdownReplay game(String id, int number, String winner, long uploadtime) {
        String log = "|uhtml|bestof|<h2><strong>Game " + number + "</strong> of <a href=\"/game-bestof3-"
                + "gen9doublescustomgame-77\">a best-of-3</a></h2>\n|player|p1|Ash|a|\n|player|p2|Misty|b|\n|win|" + winner;
        return new ShowdownReplay(id, "gen9doublescustomgame", List.of("Ash", "Misty"), log, uploadtime);
    }

    @BeforeEach
    void seed() {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE);
        league.setName("Liga import");
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0))));
        league.setSettings(LeagueSettings.defaults());
        mongoTemplate.insert(league);

        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE);
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(new DraftPick("ash", "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick("misty", "starmie", 121, 1, Instant.now(), null, null))));
        mongoTemplate.insert(draft);

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE);
        schedule.setJornadas(new ArrayList<>(List.of(new ScheduleEntity.Jornada(1, new ArrayList<>(List.of(
                new ScheduleEntity.Match("m1", "ash", "misty", null, MatchStatus.PENDING))), null))));
        mongoTemplate.insert(schedule);

        ShowdownReplay g1 = game("g1", 1, "Ash", 1_800_000_001L);
        ShowdownReplay g2 = game("g2", 2, "Misty", 1_800_000_002L);
        ShowdownReplay g3 = game("g3", 3, "Ash", 1_800_000_003L);
        when(showdownApiAdapter.searchReplays("ash", "misty")).thenReturn(List.of(
                new ShowdownReplaySummary("g3", List.of("Ash", "Misty"), g3.uploadtime()),
                new ShowdownReplaySummary("g2", List.of("Ash", "Misty"), g2.uploadtime()),
                new ShowdownReplaySummary("g1", List.of("Ash", "Misty"), g1.uploadtime())));
        when(showdownApiAdapter.getReplay("g1")).thenReturn(Optional.of(g1));
        when(showdownApiAdapter.getReplay("g2")).thenReturn(Optional.of(g2));
        when(showdownApiAdapter.getReplay("g3")).thenReturn(Optional.of(g3));
    }

    /** Los usuarios los crea el registro: después se les vincula Showdown directamente en Mongo. */
    private void linkShowdown(String username) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("name").is(username)),
                new Update().set("showdownName", username).set("showdownId", username), UserEntity.class);
    }

    @Test
    void bestOf3_listedThenImported_thenCannotReimport() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        client().loggedInAs("misty", "starmie123");
        linkShowdown("ash");
        linkShowdown("misty");

        String candidates = ash.get(BASE + "/matches/m1/replays").body();
        assertThat(candidates).contains("\"id\":\"g1\"").contains("\"gameNumber\":3").contains("\"selectable\":true");

        HttpResponse<String> imported = ash.post(BASE + "/matches/m1/import", "{\"replayIds\":[\"g1\",\"g2\",\"g3\"]}");
        assertThat(imported.statusCode()).isEqualTo(204);

        String schedule = ash.get(BASE).body();
        assertThat(schedule).contains("\"winnerUsername\":\"ash\"").contains("\"winnerScore\":2")
                .contains("\"loserScore\":1").contains("\"replayIds\":[\"g1\",\"g2\",\"g3\"]");
        LeagueEntity league = mongoTemplate.findById(LEAGUE, LeagueEntity.class);
        assertThat(league.getMembers()).extracting(LeagueMember::getCoinBalance).containsExactly(100, 50);

        HttpResponse<String> again = ash.post(BASE + "/matches/m1/import", "{\"replayIds\":[\"g1\",\"g2\",\"g3\"]}");
        assertThat(again.statusCode()).isEqualTo(409);
    }
}
