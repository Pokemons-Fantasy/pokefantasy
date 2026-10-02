package com.villu.pokefantasy.commands.activity;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.ActivityFeedResponse;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ActivityFeedFacade {

    private final Mediator mediator;

    public ActivityFeedFacade(Mediator mediator) {
        this.mediator = mediator;
    }

    /** Feed de la liga; {@code username} y {@code types} son filtros opcionales (null = sin filtro). */
    public ActivityFeedResponse getFeed(String leagueId, String username, Set<ActivityEventType> types,
                                        int page, int size, String requestingUsername) throws Exception {
        return mediator.send(new GetActivityFeedCommand(leagueId, username, types, page, size, requestingUsername));
    }
}
