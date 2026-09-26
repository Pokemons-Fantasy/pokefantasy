package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.standings.GetSeasonStatsCommand;
import com.villu.pokefantasy.commands.standings.GetStandingsCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.ScheduleResponse;
import com.villu.pokefantasy.response.SeasonStatsResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleFacade {

    private final Mediator mediator;

    public ScheduleFacade(Mediator mediator) {
        this.mediator = mediator;
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
