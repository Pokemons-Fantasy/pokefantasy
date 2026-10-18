package com.villu.pokefantasy.league;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeagueNamePolicyTest {

    @Test
    void normalize_trims() {
        assertThat(LeagueNamePolicy.normalize("  Kanto  ")).isEqualTo("Kanto");
    }

    @Test
    void normalize_acceptsFortyCharacters() {
        assertThat(LeagueNamePolicy.normalize("x".repeat(40))).hasSize(40);
    }

    @Test
    void normalize_rejectsBlankNullAndTooLong() {
        for (String name : new String[] {" ", null, "x".repeat(41)}) {
            assertThatThrownBy(() -> LeagueNamePolicy.normalize(name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El nombre de la liga debe tener entre 1 y 40 caracteres.");
        }
    }
}
