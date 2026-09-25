package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.standings.GetSeasonStatsCommand;
import com.villu.pokefantasy.commands.standings.GetStandingsCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.ScheduleResponse;
import com.villu.pokefantasy.response.SeasonStatsResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.springframework.stereotype.Service;

@Service
public class ScheduleFacade {

    private final Mediator mediator;

    public ScheduleFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public ScheduleResponse getSchedule(String leagueId, String requestingUsername) throws Exception {
        return mediator.send(new GetScheduleCommand(leagueId, requestingUsername));
    }

    public void recordResult(String leagueId, String matchId, String winnerUsername,
                             String requestingUsername) throws Exception {
        mediator.send(new RecordMatchResultCommand(leagueId, matchId, winnerUsername, requestingUsername));
    }

    /** Cambia el ganador de un partido ya registrado. */
    public void correctResult(String leagueId, String matchId, String newWinnerUsername,
                              String requestingUsername) throws Exception {
        mediator.send(new CorrectMatchResultCommand(leagueId, matchId, newWinnerUsername, requestingUsername));
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
}
