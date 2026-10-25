package com.villu.pokefantasy.commands.schedule.lineup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PokepasteCheckTest {

    @Test
    void pasteId_onlyHttpsPokepasteUrls() {
        assertThat(PokepasteCheck.pasteId("https://pokepast.es/abc123")).contains("abc123");
        assertThat(PokepasteCheck.pasteId("https://pokepast.es/abc123/")).contains("abc123");
        assertThat(PokepasteCheck.pasteId("http://pokepast.es/abc")).isEmpty();
        assertThat(PokepasteCheck.pasteId("https://example.com/x")).isEmpty();
        assertThat(PokepasteCheck.pasteId(null)).isEmpty();
    }

    @Test
    void species_handlesNicknameGenderItemAndLineEndings() {
        String paste = "Kyu (Urshifu-Rapid-Strike) (M) @ Choice Band\r\nAbility: Unseen Fist\r\n\r\n"
                + "Pikachu (F)\r\n\r\nGarchomp @ Life Orb\n- Earthquake";

        assertThat(PokepasteCheck.species(paste)).containsExactly("Urshifu-Rapid-Strike", "Pikachu", "Garchomp");
    }

    @Test
    void warnings_missingFirstThenExtra() {
        assertThat(PokepasteCheck.warnings(
                List.of("urshifu-rapid-strike", "pikachu", "garchomp"),
                List.of("Urshifu-Rapid-Strike", "Pikachu", "Raichu")))
                .containsExactly("En el paste falta garchomp.", "El paste tiene Raichu, que no está en la alineación.");
    }

    @Test
    void warnings_cappedAndNamesTruncated() {
        List<String> paste = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) paste.add("Extra" + i);
        paste.add("X".repeat(100));

        List<String> warnings = PokepasteCheck.warnings(List.of(), paste);

        assertThat(warnings).hasSize(PokepasteCheck.MAX_WARNINGS + 1);
        assertThat(warnings.getLast()).isEqualTo("…y 19 avisos más.");
        assertThat(PokepasteCheck.warnings(List.of(), List.of("X".repeat(100))).getFirst())
                .isEqualTo("El paste tiene " + "X".repeat(PokepasteCheck.MAX_NAME) + "…, que no está en la alineación.");
    }

    @Test
    void warnings_sameTeam_none() {
        assertThat(PokepasteCheck.warnings(List.of("pikachu", "mr-mime"), List.of("Mr. Mime", "Pikachu"))).isEmpty();
    }
}
