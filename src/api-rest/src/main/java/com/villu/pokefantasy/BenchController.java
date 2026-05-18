package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.bench.BenchFacade;
import com.villu.pokefantasy.request.bench.SwapWithBenchRequest;
import com.villu.pokefantasy.response.BenchEntryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/leagues/{leagueId}/bench")
public class BenchController {

    private final BenchFacade benchFacade;

    public BenchController(BenchFacade benchFacade) {
        this.benchFacade = benchFacade;
    }

    @GetMapping
    public ResponseEntity<List<BenchEntryResponse>> getBench(@PathVariable String leagueId) throws Exception {
        return ResponseEntity.ok(benchFacade.getBench(leagueId));
    }

    @PostMapping("/swap")
    public ResponseEntity<Void> swap(@PathVariable String leagueId,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     @RequestBody SwapWithBenchRequest request) throws Exception {
        benchFacade.swap(leagueId, userDetails.getUsername(), request.getPokemonToGive(), request.getPokemonToTake());
        return ResponseEntity.ok().build();
    }
}
