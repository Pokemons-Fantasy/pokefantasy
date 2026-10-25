package com.villu.pokefantasy.it;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Alineación a ciegas de punta a punta: declarar, revelar al enviar el segundo, visibilidad y bloqueo. */
class LineupIntegrationTest extends IntegrationTest {

    private static final String LEAGUE = "league-lineup";
    private static final String SCHEDULE = "/v1/leagues/" + LEAGUE + "/schedule";
    private static final String LINEUP = SCHEDULE + "/matches/m1/lineup";

    private static List<String> six(String owner) {
        return IntStream.rangeClosed(1, 6).mapToObj(i -> owner + "mon" + i).toList();
    }

    private static String body(List<String> names) {
        return "{\"pokemonNames\":[\"" + String.join("\",\"", names) + "\"]}";
    }

    @BeforeEach
    void seedLeague() {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE);
        league.setName("Liga alineaciones");
        league.setMembers(new ArrayList<>(List.of(
                new LeagueMember("ash", LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0),
                new LeagueMember("brock", LeagueRole.USER, 0))));
        league.setSettings(LeagueSettings.defaults());
        mongoTemplate.insert(league);

        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE);
        draft.setStatus(DraftStatus.COMPLETED);
        List<DraftPick> picks = new ArrayList<>();
        for (String owner : List.of("ash", "misty")) {
            six(owner).forEach(name -> picks.add(new DraftPick(owner, name, 1, 1, Instant.now(), null, null)));
        }
        draft.setPicks(picks);
        mongoTemplate.insert(draft);

        // Jornada de una semana ya pasada: el mercado está cerrado y el partido sigue pendiente.
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE);
        schedule.setJornadas(new ArrayList<>(List.of(new ScheduleEntity.Jornada(1,
                new ArrayList<>(List.of(new ScheduleEntity.Match("m1", "ash", "misty", null, MatchStatus.PENDING))),
                "2026-01-03"))));
        mongoTemplate.insert(schedule);
    }

    @Test
    void blindLineups_revealedWhenBothSent_thenLocked() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        ApiClient misty = client().loggedInAs("misty", "starmie123");
        ApiClient brock = client().loggedInAs("brock", "onix12345");

        assertThat(ash.get(SCHEDULE).body()).contains("\"lineupOpen\":true");
        assertThat(ash.put(LINEUP, body(six("ash"))).statusCode()).isEqualTo(204);

        String seenByMisty = misty.get(SCHEDULE).body();
        assertThat(seenByMisty).contains("\"player1LineupSubmitted\":true").doesNotContain("ashmon1");
        assertThat(brock.get(SCHEDULE).body()).doesNotContain("ashmon1");

        assertThat(misty.put(LINEUP, body(six("misty"))).statusCode()).isEqualTo(204);

        assertThat(ash.get(SCHEDULE).body()).contains("mistymon1").contains("ashmon1");
        assertThat(brock.get(SCHEDULE).body()).contains("mistymon1").contains("ashmon1");

        HttpResponse<String> resend = ash.put(LINEUP, body(six("ash")));
        assertThat(resend.statusCode()).isEqualTo(409);
        assertThat(resend.body()).contains("Las alineaciones ya están reveladas.");
    }

    @Test
    void notOwnPokemon_rejected() throws Exception {
        ApiClient ash = client().loggedInAs("ash", "pikachu123");
        List<String> withRival = new ArrayList<>(six("ash").subList(0, 5));
        withRival.add("mistymon1");

        HttpResponse<String> response = ash.put(LINEUP, body(withRival));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("no es de tu equipo");
    }
}
