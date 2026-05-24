package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.standings.GetStandingsCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.ScheduleResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.springframework.stereotype.Service;

@Service
public class ScheduleFacade {

    private final Mediator mediator;

    public ScheduleFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public ScheduleResponse getSchedule(String leagueId) throws Exception {
        return mediator.send(new GetScheduleCommand(leagueId));
    }

    public void recordResult(String leagueId, String matchId, String winnerUsername,
                             String requestingUsername) throws Exception {
        mediator.send(new RecordMatchResultCommand(leagueId, matchId, winnerUsername, requestingUsername));
    }

    public StandingsResponse getStandings(String leagueId) throws Exception {
        return mediator.send(new GetStandingsCommand(leagueId));
    }
}
