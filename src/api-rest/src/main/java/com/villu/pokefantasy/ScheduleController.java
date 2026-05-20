package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import com.villu.pokefantasy.request.schedule.RecordMatchResultRequest;
import com.villu.pokefantasy.response.ScheduleResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/leagues")
public class ScheduleController {

    private final ScheduleFacade scheduleFacade;

    public ScheduleController(ScheduleFacade scheduleFacade) {
        this.scheduleFacade = scheduleFacade;
    }

    @GetMapping("/{leagueId}/schedule")
    public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable String leagueId) throws Exception {
        ScheduleResponse response = scheduleFacade.getSchedule(leagueId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{leagueId}/schedule/matches/{matchId}/result")
    public ResponseEntity<Void> recordResult(@PathVariable String leagueId,
                                             @PathVariable String matchId,
                                             @AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody RecordMatchResultRequest request) throws Exception {
        scheduleFacade.recordResult(leagueId, matchId, request.getWinnerUsername(),
                userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
