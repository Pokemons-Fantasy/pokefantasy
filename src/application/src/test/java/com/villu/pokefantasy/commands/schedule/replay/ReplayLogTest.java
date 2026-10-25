package com.villu.pokefantasy.commands.schedule.replay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayLogTest {

    /** Partida 1 de una serie Bo3 real (gen9vgc2026regfbo3-2573268519), recortada. */
    private static final String BO3_GAME = String.join("\n",
            "|html|<table width=\"100%\"><tr><td align=\"left\">fowlenjoyer</td></tr></table>",
            "|uhtml|bestof|<h2><strong>Game 1</strong> of <a href=\"/game-bestof3-gen9vgc2026regfbo3-2573268516\">a best-of-3</a></h2>",
            "|gametype|doubles",
            "|player|p1|fowlenjoyer|scientist|1126",
            "|player|p2|cubeaoz|clair|1255",
            "|poke|p1|Ursaluna-Bloodmoon, L50, M|",
            "|poke|p1|Hatterene, L50, F|",
            "|poke|p2|Raging Bolt, L50|",
            "|poke|p2|Ogerpon-Wellspring, L50, F|",
            "|teamsize|p1|4",
            "|teamsize|p2|4",
            "|win|fowlenjoyer",
            "|player|p1|");

    /** Custom game real (gen9doublescustomgame-2704249592), sin serie. */
    private static final String CUSTOM = String.join("\n",
            "|gametype|doubles",
            "|player|p1|Chenpion192|taunie|",
            "|player|p2|Octopus_TW|hilda|",
            "|poke|p1|Ditto, L50|",
            "|poke|p2|Miraidon, L50|",
            "|win|Chenpion192");

    @Test
    void parse_bestOfGame_readsSeriesGameWinnerAndTeamPreview() {
        ReplayLog log = ReplayLog.parse(BO3_GAME);

        assertThat(log.p1()).isEqualTo("fowlenjoyer");
        assertThat(log.p2()).isEqualTo("cubeaoz");
        assertThat(log.winner()).isEqualTo("fowlenjoyer");
        assertThat(log.tie()).isFalse();
        assertThat(log.seriesId()).isEqualTo("gen9vgc2026regfbo3-2573268516");
        assertThat(log.gameNumber()).isEqualTo(1);
        assertThat(log.broughtBy().get("fowlenjoyer")).containsExactly("Ursaluna-Bloodmoon", "Hatterene");
        assertThat(log.broughtBy().get("cubeaoz")).containsExactly("Raging Bolt", "Ogerpon-Wellspring");
    }

    @Test
    void parse_withoutSeries_hasNoSeriesOrGame() {
        ReplayLog log = ReplayLog.parse(CUSTOM);

        assertThat(log.p1()).isEqualTo("Chenpion192");
        assertThat(log.winner()).isEqualTo("Chenpion192");
        assertThat(log.seriesId()).isNull();
        assertThat(log.gameNumber()).isNull();
    }

    @Test
    void parse_tie() {
        ReplayLog log = ReplayLog.parse("|player|p1|Ash|1|\n|player|p2|Misty|2|\n|tie");

        assertThat(log.tie()).isTrue();
        assertThat(log.winner()).isNull();
    }

    @Test
    void baseFormat_dropsCustomRules() {
        assertThat(ReplayLog.baseFormat("gen9doublescustomgame@@@Picked Team Size = 4")).isEqualTo("gen9doublescustomgame");
        assertThat(ReplayLog.baseFormat("gen9doublescustomgame")).isEqualTo("gen9doublescustomgame");
        assertThat(ReplayLog.baseFormat(null)).isEmpty();
    }
}
