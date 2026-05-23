package com.villu.pokefantasy.commands.activity;

import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.ActivityFeedResponse;
import org.springframework.stereotype.Service;

@Service
public class ActivityFeedFacade {

    private final Mediator mediator;

    public ActivityFeedFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    public ActivityFeedResponse getFeed(String leagueId, int page, int size) throws Exception {
        return mediator.send(new GetActivityFeedCommand(leagueId, page, size));
    }
}
