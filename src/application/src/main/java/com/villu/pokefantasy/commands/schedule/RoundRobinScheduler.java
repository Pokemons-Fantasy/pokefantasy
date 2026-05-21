package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a round-robin schedule (primera vuelta + segunda vuelta) for a list of players.
 * Uses the polygon/circle method: fix position 0, rotate the rest.
 * Odd number of players: a "BYE" is added to make it even; matches against BYE are skipped.
 *
 * If firstJornadaDate is provided, each jornada gets a startDate assigned:
 *   jornada N → firstJornadaDate + (N-1) weeks
 */
public class RoundRobinScheduler {

    private static final String BYE = "BYE";

    private RoundRobinScheduler() {}

    /** Generate schedule without dates (backward-compatible). */
    public static List<Jornada> generate(List<String> players) {
        return generate(players, null);
    }

    /** Generate schedule, optionally assigning a startDate to each jornada. */
    public static List<Jornada> generate(List<String> players, LocalDate firstJornadaDate) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }

        List<String> list = new ArrayList<>(players);
        if (list.size() % 2 != 0) {
            list.add(BYE);
        }

        int n = list.size();
        int roundsPerLeg = n - 1;

        List<Jornada> primerVuelta = generateLeg(list, n, roundsPerLeg, 1, firstJornadaDate);
        int segundaVueltaStart = roundsPerLeg + 1;
        LocalDate segundaVueltaOffset = firstJornadaDate != null
                ? firstJornadaDate.plusWeeks(roundsPerLeg) : null;
        List<Jornada> segundaVuelta = mirrorLeg(primerVuelta, segundaVueltaStart, segundaVueltaOffset);

        List<Jornada> all = new ArrayList<>(primerVuelta);
        all.addAll(segundaVuelta);
        return all;
    }

    private static List<Jornada> generateLeg(List<String> original, int n, int rounds,
                                              int startRound, LocalDate firstDate) {
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
            String startDate = firstDate != null
                    ? firstDate.plusWeeks(round).toString() : null;
            jornadas.add(new Jornada(startRound + round, matches, startDate));

            // Rotate: move last element to position 1 (position 0 stays fixed)
            String last = list.remove(n - 1);
            list.add(1, last);
        }

        return jornadas;
    }

    private static List<Jornada> mirrorLeg(List<Jornada> primerVuelta, int startRound,
                                            LocalDate firstDate) {
        List<Jornada> segundaVuelta = new ArrayList<>();
        for (int i = 0; i < primerVuelta.size(); i++) {
            Jornada original = primerVuelta.get(i);
            List<Match> mirroredMatches = new ArrayList<>();
            for (Match m : original.getMatches()) {
                mirroredMatches.add(new Match(
                        UUID.randomUUID().toString(),
                        m.getPlayer2(), m.getPlayer1(),
                        null, MatchStatus.PENDING));
            }
            String startDate = firstDate != null
                    ? firstDate.plusWeeks(i).toString() : null;
            segundaVuelta.add(new Jornada(startRound + i, mirroredMatches, startDate));
        }
        return segundaVuelta;
    }
}
