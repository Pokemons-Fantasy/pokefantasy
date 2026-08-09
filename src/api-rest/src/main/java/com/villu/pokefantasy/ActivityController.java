package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.activity.ActivityFeedFacade;
import com.villu.pokefantasy.response.ActivityFeedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/leagues/{leagueId}/activity")
public class ActivityController {

    private final ActivityFeedFacade activityFeedFacade;

    public ActivityController(ActivityFeedFacade activityFeedFacade) {
        this.activityFeedFacade = activityFeedFacade;
    }

    @GetMapping
    public ResponseEntity<ActivityFeedResponse> getActivityFeed(
            @PathVariable String leagueId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        if (username != null && !username.isBlank()) {
            return ResponseEntity.ok(activityFeedFacade.getFeedByUser(leagueId, username, page, size, userDetails.getUsername()));
        }
        return ResponseEntity.ok(activityFeedFacade.getFeed(leagueId, page, size, userDetails.getUsername()));
    }
}
