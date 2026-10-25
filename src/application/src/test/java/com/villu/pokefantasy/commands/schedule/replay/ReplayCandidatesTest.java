package com.villu.pokefantasy.commands.schedule.replay;

import com.villu.pokefantasy.ports.ShowdownReplay;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.MatchLineup;
import com.villu.pokefantasy.response.ReplayCandidateResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayCandidatesTest {

    private static final long FROM = 1_700_000_000L;

    private static ReplayImportContext ctx(MatchLineup ashLineup) {
        return new ReplayImportContext("ash", "misty", "ashketchum", "mistyw", "gen9doublescustomgame", FROM,
                Set.of("used-1"), ashLineup, null, 3, null);
    }

    /** Replay con Ash Ketchum y Misty W en el orden dado, ganador y serie opcionales. */
    private static ShowdownReplay replay(String id, String p1, String p2, String winner, String series, Integer game,
                                         long uploadtime, String formatid) {
        StringBuilder log = new StringBuilder();
        if (series != null) {
            log.append("|uhtml|bestof|<h2><strong>Game ").append(game)
                    .append("</strong> of <a href=\"/game-bestof3-gen9doublescustomgame-").append(series)
                    .append("\">a best-of-3</a></h2>\n");
        }
        log.append("|player|p1|").append(p1).append("|a|\n|player|p2|").append(p2).append("|b|\n");
        log.append("|poke|p1|Pikachu, L50|\n|poke|p1|Raichu, L50|\n");
        log.append(winner == null ? "|tie\n" : "|win|" + winner + "\n");
        return new ShowdownReplay(id, formatid, List.of(p1, p2), log.toString(), uploadtime);
    }

    private static ShowdownReplay ok(String id, String winner, String series, Integer game, long uploadtime) {
        return replay(id, "Ash Ketchum", "Misty W", winner, series, game, uploadtime, "gen9doublescustomgame");
    }

    @Test
    void evaluate_validReplay_mapsWinnerToLeagueUsernameEvenWithSidesSwapped() {
        ReplayCandidateResponse c = ReplayCandidates.evaluate(ctx(null),
                List.of(replay("r1", "Misty W", "Ash Ketchum", "Ash Ketchum", null, null, FROM + 10,
                        "gen9doublescustomgame"))).getFirst();

        assertThat(c.isSelectable()).isTrue();
        assertThat(c.getReason()).isNull();
        assertThat(c.getWinnerUsername()).isEqualTo("ash");
        assertThat(c.getUrl()).isEqualTo("https://replay.pokemonshowdown.com/r1");
    }

    @Test
    void evaluate_reasons() {
        List<ReplayCandidateResponse> out = ReplayCandidates.evaluate(ctx(null), List.of(
                replay("other", "Ash Ketchum", "Brock", "Brock", null, null, FROM + 1, "gen9doublescustomgame"),
                replay("format", "Ash Ketchum", "Misty W", "Misty W", null, null, FROM + 2, "gen9ou"),
                ok("tie", null, null, null, FROM + 3),
                ok("used-1", "Ash Ketchum", null, null, FROM + 4),
                ok("old", "Ash Ketchum", null, null, FROM - 1)));

        assertThat(out).extracting(ReplayCandidateResponse::getId, ReplayCandidateResponse::getReason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("other", "No son los jugadores del partido"),
                        org.assertj.core.groups.Tuple.tuple("format", "Otro formato"),
                        org.assertj.core.groups.Tuple.tuple("tie", "Empate"),
                        org.assertj.core.groups.Tuple.tuple("used-1", "Ya usado en otro partido"),
                        org.assertj.core.groups.Tuple.tuple("old", "Fuera de la semana de la jornada"));
        assertThat(out).noneMatch(ReplayCandidateResponse::isSelectable);
    }

    @Test
    void evaluate_unfinishedGame_isNotATie() {
        ShowdownReplay unfinished = new ShowdownReplay("u", "gen9doublescustomgame", List.of("Ash Ketchum", "Misty W"),
                "|player|p1|Ash Ketchum|a|\n|player|p2|Misty W|b|\n", FROM + 1);

        assertThat(ReplayCandidates.evaluate(ctx(null), List.of(unfinished)).getFirst().getReason())
                .isEqualTo("Partida sin terminar");
    }

    @Test
    void evaluate_afterTheJornadaWeek_outOfWeek() {
        long afterWeek = FROM + ReplayCandidates.WEEK_SECONDS + 1;

        assertThat(ReplayCandidates.evaluate(ctx(null), List.of(ok("late", "Ash Ketchum", null, null, afterWeek)))
                .getFirst().getReason()).isEqualTo("Fuera de la semana de la jornada");
        assertThat(ReplayCandidates.evaluate(ctx(null),
                List.of(ok("sunday", "Ash Ketchum", null, null, FROM + ReplayCandidates.WEEK_SECONDS - 1)))
                .getFirst().isSelectable()).isTrue();
    }

    @Test
    void redact_hidesPrivateReplayPassword() {
        assertThat(ReplayCandidates.redact("gen9doublescustomgame-1-secretpw")).isEqualTo("gen9doublescustomgame-1-***pw");
        assertThat(ReplayCandidates.redact("gen9doublescustomgame-1")).isEqualTo("gen9doublescustomgame-1");
    }

    @Test
    void evaluate_customRulesInFormatid_isSameFormat() {
        assertThat(ReplayCandidates.evaluate(ctx(null), List.of(replay("r", "Ash Ketchum", "Misty W", "Misty W", null,
                null, FROM + 1, "gen9doublescustomgame@@@Picked Team Size = 4"))).getFirst().isSelectable()).isTrue();
    }

    @Test
    void evaluate_withoutDate_noWeekFilter() {
        ReplayImportContext noDate = new ReplayImportContext("ash", "misty", "ashketchum", "mistyw",
                "gen9doublescustomgame", null, Set.of(), null, null, 3, null);

        assertThat(ReplayCandidates.evaluate(noDate, List.of(ok("r", "Ash Ketchum", null, null, 1L)))
                .getFirst().isSelectable()).isTrue();
    }

    @Test
    void evaluate_warnsWhenSomeoneBroughtSomethingOutsideTheirLineup() {
        MatchLineup ashLineup = new MatchLineup(List.of("pikachu", "garchomp"), null, List.of(), Instant.EPOCH);

        assertThat(ReplayCandidates.evaluate(ctx(ashLineup), List.of(ok("r", "Ash Ketchum", null, null, FROM + 1)))
                .getFirst().getWarnings())
                .containsExactly("ash llevó Raichu, que no está en su alineación.");
    }

    @Test
    void evaluate_ordersBySeriesNewestFirstThenGame_looseGamesLast() {
        List<ReplayCandidateResponse> out = ReplayCandidates.evaluate(ctx(null), List.of(
                ok("loose", "Ash Ketchum", null, null, FROM + 50),
                ok("a2", "Ash Ketchum", "100", 2, FROM + 20),
                ok("b1", "Misty W", "200", 1, FROM + 30),
                ok("a1", "Misty W", "100", 1, FROM + 10)));

        assertThat(out).extracting(ReplayCandidateResponse::getId).containsExactly("b1", "a1", "a2", "loose");
        assertThat(out.get(1).getSeriesId()).isEqualTo("gen9doublescustomgame-100");
        assertThat(out.get(1).getGameNumber()).isEqualTo(1);
    }

    private static ReplayImportContext bo(int bestOf, Long marketClosed) {
        return new ReplayImportContext("ash", "misty", "ashketchum", "mistyw", "gen9doublescustomgame", FROM,
                Set.of(), null, null, bestOf, marketClosed);
    }

    private static List<String> ids(java.util.Optional<List<ShowdownReplay>> picked) {
        return picked.orElseThrow().stream().map(ShowdownReplay::id).toList();
    }

    @Test
    void autoPick_bo3_oneCompleteSeriesAmongLooseGames_inGameOrder() {
        List<ShowdownReplay> replays = List.of(
                ok("loose", "Ash Ketchum", null, null, FROM + 1),
                ok("g3", "Ash Ketchum", "s1", 3, FROM + 30),
                ok("g1", "Ash Ketchum", "s1", 1, FROM + 10),
                ok("g2", "Misty W", "s1", 2, FROM + 20));

        assertThat(ids(ReplayCandidates.autoPick(bo(3, null), replays))).containsExactly("g1", "g2", "g3");
    }

    @Test
    void autoPick_bo3_halfUploadedSeries_nothing() {
        List<ShowdownReplay> replays = List.of(
                ok("g1", "Ash Ketchum", "s1", 1, FROM + 10),
                ok("g2", "Misty W", "s1", 2, FROM + 20));

        assertThat(ReplayCandidates.autoPick(bo(3, null), replays)).isEmpty();
    }

    @Test
    void autoPick_bo3_twoCompleteSeries_nothing() {
        List<ShowdownReplay> replays = List.of(
                ok("a1", "Ash Ketchum", "s1", 1, FROM + 10),
                ok("a2", "Ash Ketchum", "s1", 2, FROM + 20),
                ok("b1", "Misty W", "s2", 1, FROM + 30),
                ok("b2", "Misty W", "s2", 2, FROM + 40));

        assertThat(ReplayCandidates.autoPick(bo(3, null), replays)).isEmpty();
    }

    @Test
    void autoPick_bo3_gameOfOtherFormatBreaksTheSeries() {
        List<ShowdownReplay> replays = List.of(
                ok("g1", "Ash Ketchum", "s1", 1, FROM + 10),
                replay("g2", "Ash Ketchum", "Misty W", "Misty W", "s1", 2, FROM + 20, "gen9ou"),
                ok("g3", "Ash Ketchum", "s1", 3, FROM + 30));

        assertThat(ReplayCandidates.autoPick(bo(3, null), replays)).isEmpty();
    }

    @Test
    void autoPick_bo1_firstGameAfterMarketClose() {
        List<ShowdownReplay> replays = List.of(
                ok("before", "Ash Ketchum", null, null, FROM + 5),
                ok("later", "Misty W", null, null, FROM + 50),
                ok("first", "Ash Ketchum", null, null, FROM + 20));

        assertThat(ids(ReplayCandidates.autoPick(bo(1, FROM + 10), replays))).containsExactly("first");
        assertThat(ids(ReplayCandidates.autoPick(bo(1, null), replays))).containsExactly("before");
    }

    @Test
    void autoPick_noReplays_nothing() {
        assertThat(ReplayCandidates.autoPick(bo(3, null), List.of())).isEmpty();
        assertThat(ReplayCandidates.autoPick(bo(1, null), List.of())).isEmpty();
    }

    @Test
    void seriesResult_bo3_twoOne_andBo1_noScore() {
        ReplayImportContext bo3 = bo(3, null);
        List<ReplayCandidates.Evaluated> games = List.of(
                ReplayCandidates.check(bo3, ok("g1", "Ash Ketchum", "s1", 1, FROM + 10)),
                ReplayCandidates.check(bo3, ok("g2", "Misty W", "s1", 2, FROM + 20)),
                ReplayCandidates.check(bo3, ok("g3", "Ash Ketchum", "s1", 3, FROM + 30)));

        ReplayCandidates.SeriesResult result = ReplayCandidates.seriesResult(bo3, games);
        assertThat(result.winner()).isEqualTo("ash");
        assertThat(result.score()).isEqualTo(new com.villu.pokefantasy.commands.schedule.MatchScore(2, 1));

        ReplayImportContext bo1 = bo(1, null);
        ReplayCandidates.SeriesResult single = ReplayCandidates.seriesResult(bo1,
                List.of(ReplayCandidates.check(bo1, ok("x", "Misty W", null, null, FROM + 1))));
        assertThat(single.winner()).isEqualTo("misty");
        assertThat(single.score()).isNull();
    }
}
