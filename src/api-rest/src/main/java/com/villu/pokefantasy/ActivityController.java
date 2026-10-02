package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.activity.ActivityFeedFacade;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.response.ActivityFeedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/v1/leagues/{leagueId}/activity")
public class ActivityController {

    private final ActivityFeedFacade activityFeedFacade;

    public ActivityController(ActivityFeedFacade activityFeedFacade) {
        this.activityFeedFacade = activityFeedFacade;
    }

    /**
     * Feed de la liga, del más reciente al más antiguo. {@code username}: solo los eventos de ese jugador.
     * {@code types}: solo esos tipos ({@code ?types=STEAL,TRADE_COMPLETED}); un tipo desconocido da 400.
     */
    @GetMapping
    public ResponseEntity<ActivityFeedResponse> getActivityFeed(
            @PathVariable String leagueId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Set<ActivityEventType> types,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(activityFeedFacade.getFeed(leagueId, username, types, page, size, userDetails.getUsername()));
    }
}
