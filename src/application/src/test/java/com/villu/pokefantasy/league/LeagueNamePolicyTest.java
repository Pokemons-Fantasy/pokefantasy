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
    void normalize_rejectsControlCharacters() {
        // Un salto de línea en el nombre falsearía líneas del log y se pintaría raro en la web.
        assertThatThrownBy(() -> LeagueNamePolicy.normalize("Kanto\nINFO Admin root deleted user ash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre de la liga no puede tener saltos de línea ni caracteres de control.");
        assertThatThrownBy(() -> LeagueNamePolicy.normalize("Kan\u0000to")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forLog_replacesControlCharactersOfOldNames() {
        assertThat(LeagueNamePolicy.forLog("Kanto\r\nfalso")).isEqualTo("Kanto??falso");
        assertThat(LeagueNamePolicy.forLog(null)).isEqualTo("null");
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
