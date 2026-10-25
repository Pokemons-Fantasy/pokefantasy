package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.ReplayCandidates;
import com.villu.pokefantasy.commands.schedule.replay.ReplayCandidates.Evaluated;
import com.villu.pokefantasy.commands.schedule.replay.ReplayCandidates.SeriesResult;
import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
        LeagueEntity league = command.username() == null ? leagueMembershipGuard.requireLeague(command.leagueId())
                : leagueMembershipGuard.requireMember(command.leagueId(), command.username());
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
                .sorted(ReplayCandidates.GAME_ORDER)
                .toList();
        games.forEach(g -> g.reason().ifPresent(reason -> {
            throw new IllegalArgumentException(ReplayCandidates.redact(g.replay().id()) + ": " + reason + ".");
        }));
        SeriesResult result = ReplayCandidates.seriesResult(ctx, games);

        Match match = matchResultService.findMatch(schedule, command.matchId());
        String loser = matchResultService.requireParticipant(match, result.winner());
        matchResultService.requireWinnerHasTeam(command.leagueId(), result.winner(), loser);
        matchResultService.award(league, match, result.winner(), loser, result.score(),
                matchResultService.findRoundNumber(schedule, command.matchId()));

        match.setReplayIds(games.stream().map(g -> g.replay().id()).toList());
        List<String> warnings = games.stream().flatMap(g -> g.warnings().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().limit(MAX_WARNINGS).toList();
        match.setImportWarnings(warnings.isEmpty() ? null : warnings);
        scheduleRepository.save(schedule);
        return null;
    }

    @Override
    public Class<ImportSeriesCommand> commandType() {
        return ImportSeriesCommand.class;
    }
}
