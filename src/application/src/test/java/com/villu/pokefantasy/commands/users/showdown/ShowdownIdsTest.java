package com.villu.pokefantasy.commands.users.showdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShowdownIdsTest {

    @Test
    void toId_lowercasesAndDropsNonAlphanumerics() {
        assertThat(ShowdownIds.toId("Ash Ketchum!")).isEqualTo("ashketchum");
    }

    @Test
    void toId_blankOrNull_isEmpty() {
        assertThat(ShowdownIds.toId("  ")).isEmpty();
        assertThat(ShowdownIds.toId(null)).isEmpty();
    }
}
