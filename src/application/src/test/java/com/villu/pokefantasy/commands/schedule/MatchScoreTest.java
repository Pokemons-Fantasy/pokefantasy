package com.villu.pokefantasy.commands.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchScoreTest {

    @Test
    void of_bothNull_meansNoScore() {
        assertThat(MatchScore.of(null, null)).isNull();
    }

    @Test
    void of_validScore() {
        assertThat(MatchScore.of(3, 1)).isEqualTo(new MatchScore(3, 1));
        assertThat(MatchScore.of(1, 0)).isEqualTo(new MatchScore(1, 0));
    }

    @Test
    void of_onlyOneSide_rejected() {
        assertThatThrownBy(() -> MatchScore.of(3, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchScore.of(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void winnerMustHaveMore() {
        assertThatThrownBy(() -> new MatchScore(2, 2)).hasMessageContaining("más que el perdedor");
        assertThatThrownBy(() -> new MatchScore(1, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outOfRange_rejected() {
        assertThatThrownBy(() -> new MatchScore(3, -1)).hasMessageContaining("entre 0 y 99");
        assertThatThrownBy(() -> new MatchScore(100, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchScore(-1, -2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchScore(5, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
