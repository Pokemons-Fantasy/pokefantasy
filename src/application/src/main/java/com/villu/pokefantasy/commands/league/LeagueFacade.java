package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.LeagueDetailResponse;
import com.villu.pokefantasy.response.LeagueResponse;
import com.villu.pokefantasy.response.LeagueSettingsResponse;
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

    public void removeMember(String leagueId, String targetUsername, String requestingUsername) throws Exception {
        mediator.send(new RemoveMemberFromLeagueCommand(leagueId, targetUsername, requestingUsername));
    }

    public LeagueSettingsResponse getSettings(String leagueId) throws Exception {
        return mediator.send(new GetLeagueSettingsCommand(leagueId));
    }

    public void updateSettings(String leagueId, Integer coinsPerWin, Integer coinsPerLoss,
                               Integer priceTierS, Integer priceTierA, Integer priceTierB,
                               Integer priceTierC, Integer priceTierD,
                               String requestingUsername) throws Exception {
        mediator.send(new UpdateLeagueSettingsCommand(leagueId, coinsPerWin, coinsPerLoss,
                priceTierS, priceTierA, priceTierB, priceTierC, priceTierD, requestingUsername));
    }
}
