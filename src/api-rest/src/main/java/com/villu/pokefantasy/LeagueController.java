package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.league.LeagueFacade;
import com.villu.pokefantasy.request.league.AddMemberRequest;
import com.villu.pokefantasy.request.league.CreateLeagueRequest;
import com.villu.pokefantasy.request.league.SetLeagueMvpRequest;
import com.villu.pokefantasy.request.league.UpdateLeagueSettingsRequest;
import com.villu.pokefantasy.response.CoinBalanceResponse;
import com.villu.pokefantasy.response.LeagueDetailResponse;
import com.villu.pokefantasy.response.LeagueResponse;
import com.villu.pokefantasy.response.LeagueSettingsResponse;
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

    @DeleteMapping("/{leagueId}/members/{username}")
    public ResponseEntity<Void> removeMember(@PathVariable String leagueId,
                                             @PathVariable String username,
                                             @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        leagueFacade.removeMember(leagueId, username, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{leagueId}/settings")
    public ResponseEntity<LeagueSettingsResponse> getSettings(@PathVariable String leagueId) throws Exception {
        return ResponseEntity.ok(leagueFacade.getSettings(leagueId));
    }

    @PutMapping("/{leagueId}/settings")
    public ResponseEntity<Void> updateSettings(@PathVariable String leagueId,
                                               @AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody UpdateLeagueSettingsRequest request) throws Exception {
        leagueFacade.updateSettings(leagueId, request.getCoinsPerWin(), request.getCoinsPerLoss(),
                request.getPriceTierS(), request.getPriceTierA(), request.getPriceTierB(),
                request.getPriceTierC(), request.getPriceTierD(),
                request.getSeasonStartDate(), request.getMaxTeamSize(),
                request.getTierPctS(), request.getTierPctA(), request.getTierPctB(),
                request.getTierPctC(), request.getTierPctD(),
                request.getTurnTimerSeconds(),
                request.getStealWindowCloseDay(), request.getStealWindowCloseTime(),
                request.getSwapWindowCloseDay(), request.getSwapWindowCloseTime(),
                userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{leagueId}/my-coins")
    public ResponseEntity<CoinBalanceResponse> getMyCoinBalance(@PathVariable String leagueId,
                                                                @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        int coins = leagueFacade.getMyCoinBalance(leagueId, userDetails.getUsername());
        return ResponseEntity.ok(CoinBalanceResponse.builder().coins(coins).build());
    }

    @PutMapping("/{leagueId}/my-mvp")
    public ResponseEntity<Void> setMyMvp(@PathVariable String leagueId,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         @RequestBody SetLeagueMvpRequest request) throws Exception {
        leagueFacade.setMyMvp(leagueId, userDetails.getUsername(), request.getPokemonName());
        return ResponseEntity.ok().build();
    }
}
