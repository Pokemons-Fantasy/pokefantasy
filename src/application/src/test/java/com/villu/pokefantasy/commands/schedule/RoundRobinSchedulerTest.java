package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinSchedulerTest {

    @Test
    void generate_fourPlayers_producesSixJornadas() {
        List<String> players = List.of("ash", "brock", "misty", "gary");
        List<Jornada> jornadas = RoundRobinScheduler.generate(players);

        assertThat(jornadas).hasSize(6); // 3 primera vuelta + 3 segunda vuelta
    }

    @Test
    void generate_fourPlayers_eachJornadaHasTwoMatches() {
        List<String> players = List.of("ash", "brock", "misty", "gary");
        List<Jornada> jornadas = RoundRobinScheduler.generate(players);

        for (Jornada j : jornadas) {
            assertThat(j.getMatches()).hasSize(2);
        }
    }

    @Test
    void generate_fourPlayers_eachPairAppearsTwice() {
        List<String> players = List.of("ash", "brock", "misty", "gary");
        List<Jornada> jornadas = RoundRobinScheduler.generate(players);

        // Build a multiset of unordered pairs
        java.util.Map<String, Integer> pairCount = new java.util.HashMap<>();
        for (Jornada j : jornadas) {
            for (Match m : j.getMatches()) {
                String key = normalize(m.getPlayer1(), m.getPlayer2());
                pairCount.merge(key, 1, Integer::sum);
            }
        }

        // 4 players → 6 unique pairs; each should appear exactly 2 times (one per leg)
        assertThat(pairCount).hasSize(6);
        pairCount.values().forEach(count -> assertThat(count).isEqualTo(2));
    }

    @Test
    void generate_fourPlayers_roundNumbersAreConsecutive() {
        List<Jornada> jornadas = RoundRobinScheduler.generate(List.of("ash", "brock", "misty", "gary"));

        for (int i = 0; i < jornadas.size(); i++) {
            assertThat(jornadas.get(i).getRoundNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    void generate_fourPlayers_secondLegHasMirroredPlayers() {
        List<String> players = List.of("ash", "brock", "misty", "gary");
        List<Jornada> jornadas = RoundRobinScheduler.generate(players);

        List<Jornada> primera = jornadas.subList(0, 3);
        List<Jornada> segunda = jornadas.subList(3, 6);

        for (int i = 0; i < 3; i++) {
            List<Match> pm = primera.get(i).getMatches();
            List<Match> sm = segunda.get(i).getMatches();
            assertThat(pm).hasSameSizeAs(sm);
            for (int j = 0; j < pm.size(); j++) {
                // Second leg swaps player1 and player2
                assertThat(sm.get(j).getPlayer1()).isEqualTo(pm.get(j).getPlayer2());
                assertThat(sm.get(j).getPlayer2()).isEqualTo(pm.get(j).getPlayer1());
            }
        }
    }

    @Test
    void generate_threePlayersOdd_producesFourJornadas() {
        List<String> players = List.of("ash", "brock", "misty");
        List<Jornada> jornadas = RoundRobinScheduler.generate(players);

        // 3 players → add BYE → 4 players → 3 rounds per leg → 6 jornadas total
        // but only 1 real match per jornada (one match is BYE)
        assertThat(jornadas).hasSize(6);
        jornadas.forEach(j -> assertThat(j.getMatches()).hasSize(1));
    }

    @Test
    void generate_twoPlayers_producesTwoJornadas() {
        List<Jornada> jornadas = RoundRobinScheduler.generate(List.of("ash", "brock"));

        assertThat(jornadas).hasSize(2);
        assertThat(jornadas.get(0).getMatches()).hasSize(1);
        assertThat(jornadas.get(1).getMatches()).hasSize(1);
    }

    @Test
    void generate_allMatchesPending() {
        List<Jornada> jornadas = RoundRobinScheduler.generate(List.of("ash", "brock", "misty", "gary"));

        jornadas.stream()
                .flatMap(j -> j.getMatches().stream())
                .forEach(m -> assertThat(m.getStatus()).isEqualTo(MatchStatus.PENDING));
    }

    @Test
    void generate_allMatchHaveUniqueIds() {
        List<Jornada> jornadas = RoundRobinScheduler.generate(List.of("ash", "brock", "misty", "gary"));

        Set<String> ids = jornadas.stream()
                .flatMap(j -> j.getMatches().stream())
                .map(Match::getId)
                .collect(Collectors.toSet());

        long total = jornadas.stream().mapToLong(j -> j.getMatches().size()).sum();
        assertThat(ids).hasSize((int) total);
    }

    @Test
    void generate_emptyPlayers_returnsEmpty() {
        assertThat(RoundRobinScheduler.generate(List.of())).isEmpty();
    }

    // Helper: canonical pair key regardless of player order
    private String normalize(String p1, String p2) {
        return p1.compareTo(p2) < 0 ? p1 + "|" + p2 : p2 + "|" + p1;
    }
}
