package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.standings.GetSeasonStatsCommand;
import com.villu.pokefantasy.commands.standings.GetStandingsCommand;
import com.villu.pokefantasy.commands.schedule.lineup.PokepasteCheck;
import com.villu.pokefantasy.commands.schedule.replay.ReplayCandidates;
import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.ports.ShowdownPort;
import com.villu.pokefantasy.ports.ShowdownReplay;
import com.villu.pokefantasy.response.ReplayCandidateResponse;
import com.villu.pokefantasy.response.ScheduleResponse;
import com.villu.pokefantasy.response.SeasonStatsResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ScheduleFacade {

    private final Mediator mediator;
    private final ShowdownPort showdownPort;

    public ScheduleFacade(Mediator mediator, ShowdownPort showdownPort) {
        this.mediator = mediator;
        this.showdownPort = showdownPort;
    }

    /**
     * Declara la alineación del partido. Si el enlace es de pokepast.es, se lee aquí, antes del comando (fuera
     * de la transacción, ADR-007), y sus diferencias con la alineación viajan como avisos: nunca bloquean.
     */
    public void declareLineup(String leagueId, String matchId, List<String> pokemonNames, String pasteUrl,
                              String requestingUsername) throws Exception {
        List<String> warnings = PokepasteCheck.pasteId(pasteUrl)
                .map(id -> pasteWarnings(id, pokemonNames))
                .orElse(List.of());
        mediator.send(new DeclareLineupCommand(leagueId, matchId, requestingUsername, pokemonNames, pasteUrl,
                warnings));
    }

    private List<String> pasteWarnings(String pasteId, List<String> pokemonNames) {
        try {
            return showdownPort.getPokepaste(pasteId)
                    .map(paste -> PokepasteCheck.warnings(pokemonNames, PokepasteCheck.species(paste)))
                    .orElse(List.of(PASTE_UNREADABLE));
        } catch (ShowdownUnavailableException e) {
            return List.of(PASTE_UNREADABLE);
        }
    }

    static final String PASTE_UNREADABLE = "No se pudo leer el paste.";

    /**
     * Replays de Showdown que podrían ser las partidas del partido. Con {@code url} (un replay privado pegado a
     * mano), solo ese; si no, los públicos entre los dos jugadores desde la semana de la jornada (como mucho
     * {@link ReplayCandidates#MAX_SEARCHED}). Las descargas se hacen aquí, fuera de la transacción (ADR-007).
     */
    public List<ReplayCandidateResponse> replayCandidates(String leagueId, String matchId, String url,
                                                          String requestingUsername) throws Exception {
        ReplayImportContext ctx = mediator.send(new PrepareReplayImportCommand(leagueId, matchId, requestingUsername));
        List<ShowdownReplay> replays;
        if (url != null && !url.isBlank()) {
            String id = ReplayCandidates.replayId(url)
                    .orElseThrow(() -> new IllegalArgumentException("Pega el enlace de un replay de Showdown."));
            replays = List.of(fetchReplay(id));
        } else {
            replays = showdownPort.searchReplays(ctx.player1Id(), ctx.player2Id()).stream()
                    .filter(s -> ctx.fromEpoch() == null || s.uploadtime() >= ctx.fromEpoch())
                    .limit(ReplayCandidates.MAX_SEARCHED)
                    .map(s -> showdownPort.getReplay(s.id()))
                    .flatMap(Optional::stream)
                    .toList();
        }
        return ReplayCandidates.evaluate(ctx, replays);
    }

    /** Partidas que se pueden marcar como mucho (una serie al mejor de 3). */
    static final int MAX_IMPORTED = 3;

    /**
     * Registra el resultado con las partidas marcadas. Del cliente solo se usan los ids: cada replay se vuelve a
     * descargar aquí (fuera de la transacción, ADR-007) y el comando lo valida entero.
     */
    public void importSeries(String leagueId, String matchId, List<String> replayIds, String requestingUsername)
            throws Exception {
        if (replayIds == null || replayIds.isEmpty()) {
            throw new IllegalArgumentException("Marca las partidas de la serie.");
        }
        if (replayIds.size() > MAX_IMPORTED) {
            throw new IllegalArgumentException("Marca como mucho " + MAX_IMPORTED + " partidas.");
        }
        // Antes de descargar: un id raro ("../x") haría peticiones arbitrarias a replay.pokemonshowdown.com.
        if (replayIds.stream().anyMatch(id -> !ReplayCandidates.isReplayId(id))) {
            throw new IllegalArgumentException("Replay no válido.");
        }
        List<ShowdownReplay> replays = replayIds.stream().map(this::fetchReplay).toList();
        mediator.send(new ImportSeriesCommand(leagueId, matchId, requestingUsername, replays));
    }

    private ShowdownReplay fetchReplay(String id) {
        return showdownPort.getReplay(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el replay " + id + "."));
    }

    public ScheduleResponse getSchedule(String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new GetScheduleCommand(leagueId, requestingUsername));
    }

    public void recordResult(String leagueId, String matchId, String winnerUsername, MatchScore score,
                             String requestingUsername) throws Exception {
        mediator.send(new RecordMatchResultCommand(leagueId, matchId, winnerUsername, score, requestingUsername));
    }

    /** Cambia el ganador o el marcador de un partido ya registrado. */
    public void correctResult(String leagueId, String matchId, String newWinnerUsername, MatchScore score,
                              String requestingUsername) throws Exception {
        mediator.send(new CorrectMatchResultCommand(leagueId, matchId, newWinnerUsername, score, requestingUsername));
    }

    /** Deshace el resultado de un partido: vuelve a pendiente y se devuelven las monedas. */
    public void revertResult(String leagueId, String matchId, String requestingUsername) throws Exception {
        mediator.send(new CorrectMatchResultCommand(leagueId, matchId, null, requestingUsername));
    }

    public StandingsResponse getStandings(String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new GetStandingsCommand(leagueId, requestingUsername));
    }

    public SeasonStatsResponse getSeasonStats(String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new GetSeasonStatsCommand(leagueId, requestingUsername));
    }

    /** Ligas con algún aviso de cierre de ventana pendiente (para el job). */
    public List<String> leaguesWithDueWindowReminders() throws Exception {
        return mediator.send(new ListDueWindowRemindersCommand());
    }

    /** Envía los avisos de cierre de ventana pendientes de la liga; {@code true} si envió alguno. */
    public boolean sendWindowReminders(String leagueId) throws Exception {
        return mediator.send(new SendWindowRemindersCommand(leagueId));
    }
}
