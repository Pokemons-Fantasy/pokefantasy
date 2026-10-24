package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.standings.GetSeasonStatsCommand;
import com.villu.pokefantasy.commands.standings.GetStandingsCommand;
import com.villu.pokefantasy.commands.schedule.lineup.PokepasteCheck;
import com.villu.pokefantasy.exception.ShowdownUnavailableException;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.ports.ShowdownPort;
import com.villu.pokefantasy.response.ScheduleResponse;
import com.villu.pokefantasy.response.SeasonStatsResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

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
