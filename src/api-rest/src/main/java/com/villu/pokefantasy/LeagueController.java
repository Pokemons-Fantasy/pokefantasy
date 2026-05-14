package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.league.LeagueFacade;
import com.villu.pokefantasy.request.league.AddMemberRequest;
import com.villu.pokefantasy.request.league.CreateLeagueRequest;
import com.villu.pokefantasy.response.LeagueDetailResponse;
import com.villu.pokefantasy.response.LeagueResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/leagues")
public class LeagueController {

    private final LeagueFacade leagueFacade;

    public LeagueController(LeagueFacade leagueFacade) {
        this.leagueFacade = leagueFacade;
    }

    @PostMapping
    public ResponseEntity<String> createLeague(@AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody CreateLeagueRequest request) throws Exception {
        String leagueId = leagueFacade.createLeague(request.getName(), userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(leagueId);
    }

    @PostMapping("/{leagueId}/members")
    public ResponseEntity<Void> addMember(@PathVariable String leagueId,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          @RequestBody AddMemberRequest request) throws Exception {
        leagueFacade.addMember(leagueId, request.getUsername(), userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<LeagueResponse>> getMyLeagues(@AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(leagueFacade.getMyLeagues(userDetails.getUsername()));
    }

    @GetMapping("/{leagueId}")
    public ResponseEntity<LeagueDetailResponse> getLeagueDetail(@PathVariable String leagueId) throws Exception {
        return ResponseEntity.ok(leagueFacade.getLeagueDetail(leagueId));
    }
}
