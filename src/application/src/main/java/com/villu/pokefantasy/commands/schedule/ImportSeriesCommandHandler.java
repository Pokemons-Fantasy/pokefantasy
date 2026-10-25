package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.ReplayCandidates;
import com.villu.pokefantasy.commands.schedule.replay.ReplayCandidates.Evaluated;
import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registra el resultado de un partido a partir de los replays de su serie: los valida con las mismas reglas que
 * la lista de candidatos, comprueba que forman una serie válida según {@code bestOf} y registra con
 * {@link MatchResultService} (monedas y eventos como un resultado a mano).
 */
@Service
public class ImportSeriesCommandHandler implements CommandHandler<ImportSeriesCommand, Void> {

    static final int MAX_WARNINGS = 12;

    private final ScheduleRepository scheduleRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;
    private final ReplayImportService replayImportService;
    private final MatchResultService matchResultService;

    public ImportSeriesCommandHandler(ScheduleRepository scheduleRepository, LeagueMembershipGuard leagueMembershipGuard,
                                      ReplayImportService replayImportService, MatchResultService matchResultService) {
        this.scheduleRepository = scheduleRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.replayImportService = replayImportService;
        this.matchResultService = matchResultService;
    }

    @Override
    public Void handle(ImportSeriesCommand command) {
        LeagueEntity league = leagueMembershipGuard.requireMember(command.leagueId(), command.username());
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException(
                        "Esta liga aún no tiene calendario: se genera al completar el draft."));
        ReplayImportContext ctx = replayImportService.contextFor(league, schedule, command.matchId(), command.username());

        List<String> ids = command.replays().stream().map(r -> r.id()).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("No marques la misma partida dos veces.");
        }
        List<Evaluated> games = command.replays().stream()
                .map(r -> ReplayCandidates.check(ctx, r))
                .sorted(Comparator.comparing((Evaluated e) -> e.log().gameNumber() == null ? 0 : e.log().gameNumber())
                        .thenComparing(e -> e.replay().uploadtime()))
                .toList();
        games.forEach(g -> g.reason().ifPresent(reason -> {
            throw new IllegalArgumentException(g.replay().id() + ": " + reason + ".");
        }));

        Map<String, Long> wins = games.stream().collect(Collectors.groupingBy(Evaluated::winnerUsername, Collectors.counting()));
        String winner = wins.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        MatchScore score = seriesScore(ctx.bestOf(), games.size(), wins, winner);

        Match match = matchResultService.findMatch(schedule, command.matchId());
        String loser = matchResultService.requireParticipant(match, winner);
        matchResultService.requireWinnerHasTeam(command.leagueId(), winner, loser);
        matchResultService.award(league, match, winner, loser, score,
                matchResultService.findRoundNumber(schedule, command.matchId()));

        match.setReplayIds(games.stream().map(g -> g.replay().id()).toList());
        List<String> warnings = games.stream().flatMap(g -> g.warnings().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().limit(MAX_WARNINGS).toList();
        match.setImportWarnings(warnings.isEmpty() ? null : warnings);
        scheduleRepository.save(schedule);
        return null;
    }

    /** Marcador de la serie, o error si las partidas marcadas no forman una serie completa. Bo1: sin marcador. */
    private static MatchScore seriesScore(int bestOf, int games, Map<String, Long> wins, String winner) {
        if (bestOf == 1) {
            if (games != 1) throw new IllegalArgumentException("Marca una partida.");
            return null;
        }
        long winnerWins = winner == null ? 0 : wins.get(winner);
        long loserWins = games - winnerWins;
        boolean valid = winnerWins == 2 && (loserWins == 0 || loserWins == 1);
        if (!valid) {
            throw new IllegalArgumentException(
                    "Para una serie al mejor de 3 marca 2 partidas del mismo ganador o 3 con un 2-1.");
        }
        return new MatchScore(2, (int) loserWins);
    }

    @Override
    public Class<ImportSeriesCommand> commandType() {
        return ImportSeriesCommand.class;
    }
}
