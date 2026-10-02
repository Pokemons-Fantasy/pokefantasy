package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnOrderPolicyTest {

    private final TurnOrderPolicy policy = new TurnOrderPolicy();

    private static LeagueEntity league(String... members) {
        LeagueEntity league = new LeagueEntity();
        List<LeagueMember> list = new ArrayList<>();
        for (String m : members) list.add(new LeagueMember(m, LeagueRole.USER, 0));
        league.setMembers(list);
        return league;
    }

    @Test
    void canonical_usesLeagueNamesAndTrims() {
        assertThat(policy.canonical(List.of(" brock ", "ASH"), league("ash", "Brock"))).containsExactly("Brock", "ash");
    }

    @Test
    void canonical_rejectsEmptyBlankDuplicatesStrangersAndMissing() {
        assertThatThrownBy(() -> policy.canonical(null, league("ash"))).hasMessageContaining("al menos un jugador");
        assertThatThrownBy(() -> policy.canonical(List.of(), league("ash"))).hasMessageContaining("al menos un jugador");
        assertThatThrownBy(() -> policy.canonical(List.of("ash", " "), league("ash"))).hasMessageContaining("sin nombre");
        assertThatThrownBy(() -> policy.canonical(List.of("ash", "ASH"), league("ash"))).hasMessageContaining("repetidos");
        assertThatThrownBy(() -> policy.canonical(List.of("ash", "gary"), league("ash"))).hasMessage("No son miembros de la liga: gary");
        assertThatThrownBy(() -> policy.canonical(List.of("ash"), league("ash", "misty"))).hasMessage("Faltan en el orden de turnos: misty");
    }
}
