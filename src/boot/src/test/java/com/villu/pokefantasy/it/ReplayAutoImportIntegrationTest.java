package com.villu.pokefantasy.it;

import com.villu.pokefantasy.ReplayAutoImportJob;
import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Con el mercado cerrado, el job registra solo una serie 2-0 ya subida y, al ser el último partido, marca la jornada como avisada. */
class ReplayAutoImportIntegrationTest extends IntegrationTest {

    private static final String LEAGUE = "league-auto-import";

    @Autowired
    private ReplayAutoImportJob job;

    private static ShowdownReplay game(String id, int number, long uploadtime) {
        String log = "|uhtml|bestof|<h2><strong>Game " + number + "</strong> of <a href=\"/game-bestof3-"
                + "gen9doublescustomgame-88\">a best-of-3</a></h2>\n|player|p1|Brock|a|\n|player|p2|Gary|b|\n|win|Gary";
        return new ShowdownReplay(id, "gen9doublescustomgame", List.of("Brock", "Gary"), log, uploadtime);
    }

    private void linkShowdown(String username) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("name").is(username)),
                new Update().set("showdownName", username).set("showdownId", username), UserEntity.class);
    }

    @Test
    void completeSeriesUploaded_registeredByTheJob_andJornadaMarkedNotified() throws Exception {
        client().loggedInAs("brock", "onix12345");
        client().loggedInAs("gary", "eevee12345");
        linkShowdown("brock");
        linkShowdown("gary");

        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE);
        league.setName("Liga auto");
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("brock", LeagueRole.ADMIN, 0),
                new LeagueMember("gary", LeagueRole.USER, 0))));
        league.setSettings(LeagueSettings.defaults());
        mongoTemplate.insert(league);

        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE);
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(new DraftPick("brock", "onix", 95, 1, Instant.now(), null, null),
                new DraftPick("gary", "eevee", 133, 1, Instant.now(), null, null))));
        mongoTemplate.insert(draft);

        // Jornada de la semana pasada: su mercado ya cerró y los replays de esa semana valen.
        LocalDate monday = LocalDate.now(JornadaWindowService.LEAGUE_ZONE).minusWeeks(1).with(DayOfWeek.MONDAY);
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE);
        schedule.setJornadas(new ArrayList<>(List.of(new ScheduleEntity.Jornada(1, new ArrayList<>(List.of(
                new ScheduleEntity.Match("m1", "brock", "gary", null, MatchStatus.PENDING))), monday.toString()))));
        mongoTemplate.insert(schedule);

        long saturday = monday.plusDays(5).atStartOfDay(JornadaWindowService.LEAGUE_ZONE).toEpochSecond();
        ShowdownReplay g1 = game("a1", 1, saturday + 1);
        ShowdownReplay g2 = game("a2", 2, saturday + 2);
        when(showdownApiAdapter.searchReplays("brock", "gary")).thenReturn(List.of(
                new ShowdownReplaySummary("a2", List.of("Brock", "Gary"), g2.uploadtime()),
                new ShowdownReplaySummary("a1", List.of("Brock", "Gary"), g1.uploadtime())));
        when(showdownApiAdapter.getReplay("a1")).thenReturn(Optional.of(g1));
        when(showdownApiAdapter.getReplay("a2")).thenReturn(Optional.of(g2));

        job.importReplays();

        ScheduleEntity saved = mongoTemplate.findOne(Query.query(Criteria.where("leagueId").is(LEAGUE)), ScheduleEntity.class);
        ScheduleEntity.Jornada jornada = saved.getJornadas().getFirst();
        ScheduleEntity.Match match = jornada.getMatches().getFirst();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(match.getWinnerUsername()).isEqualTo("gary");
        assertThat(match.getWinnerScore()).isEqualTo(2);
        assertThat(match.getLoserScore()).isEqualTo(0);
        assertThat(match.getReplayIds()).containsExactly("a1", "a2");
        assertThat(jornada.getCompletionNotified()).isTrue();
    }
}
