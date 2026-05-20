package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a round-robin schedule (primera vuelta + segunda vuelta) for a list of players.
 * Uses the polygon/circle method: fix position 0, rotate the rest.
 * Odd number of players: a "BYE" is added to make it even; matches against BYE are skipped.
 */
public class RoundRobinScheduler {

    private static final String BYE = "BYE";

    private RoundRobinScheduler() {}

    public static List<Jornada> generate(List<String> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }

        List<String> list = new ArrayList<>(players);
        if (list.size() % 2 != 0) {
            list.add(BYE);
        }

        int n = list.size();
        int roundsPerLeg = n - 1;

        List<Jornada> primerVuelta = generateLeg(list, n, roundsPerLeg, 1);
        List<Jornada> segundaVuelta = mirrorLeg(primerVuelta, roundsPerLeg + 1);

        List<Jornada> all = new ArrayList<>(primerVuelta);
        all.addAll(segundaVuelta);
        return all;
    }

    private static List<Jornada> generateLeg(List<String> original, int n, int rounds, int startRound) {
        // Work on a mutable copy for rotation
        List<String> list = new ArrayList<>(original);
        List<Jornada> jornadas = new ArrayList<>();

        for (int round = 0; round < rounds; round++) {
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                String p1 = list.get(i);
                String p2 = list.get(n - 1 - i);
                if (!BYE.equals(p1) && !BYE.equals(p2)) {
                    matches.add(new Match(UUID.randomUUID().toString(), p1, p2, null, MatchStatus.PENDING));
                }
            }
            jornadas.add(new Jornada(startRound + round, matches));

            // Rotate: move last element to position 1 (position 0 stays fixed)
            String last = list.remove(n - 1);
            list.add(1, last);
        }

        return jornadas;
    }

    private static List<Jornada> mirrorLeg(List<Jornada> primerVuelta, int startRound) {
        List<Jornada> segundaVuelta = new ArrayList<>();
        for (int i = 0; i < primerVuelta.size(); i++) {
            Jornada original = primerVuelta.get(i);
            List<Match> mirroredMatches = new ArrayList<>();
            for (Match m : original.getMatches()) {
                // Swap player1 and player2 for the second leg
                mirroredMatches.add(new Match(UUID.randomUUID().toString(), m.getPlayer2(), m.getPlayer1(), null, MatchStatus.PENDING));
            }
            segundaVuelta.add(new Jornada(startRound + i, mirroredMatches));
        }
        return segundaVuelta;
    }
}
