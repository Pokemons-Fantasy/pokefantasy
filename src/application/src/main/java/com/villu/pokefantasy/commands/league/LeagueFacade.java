package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.LeagueDetailResponse;
import com.villu.pokefantasy.response.LeagueResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeagueFacade {

    private final Mediator mediator;

    public LeagueFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public String createLeague(String name, String creatorUsername) throws Exception {
        return mediator.send(new CreateLeagueCommand(name, creatorUsername));
    }

    public void addMember(String leagueId, String targetUsername, String requestingUsername) throws Exception {
        mediator.send(new AddMemberToLeagueCommand(leagueId, targetUsername, requestingUsername));
    }

    public List<LeagueResponse> getMyLeagues(String username) throws Exception {
        return mediator.send(new GetMyLeaguesCommand(username));
    }

    public LeagueDetailResponse getLeagueDetail(String leagueId) throws Exception {
        return mediator.send(new GetLeagueDetailCommand(leagueId));
    }
}
