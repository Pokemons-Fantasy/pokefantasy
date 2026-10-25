package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.schedule.MatchScore;
import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import com.villu.pokefantasy.request.schedule.DeclareLineupRequest;
import com.villu.pokefantasy.request.schedule.ImportSeriesRequest;
import com.villu.pokefantasy.request.schedule.RecordMatchResultRequest;
import com.villu.pokefantasy.response.ReplayCandidateResponse;
import com.villu.pokefantasy.response.ScheduleResponse;
import com.villu.pokefantasy.response.SeasonStatsResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/leagues")
public class ScheduleController {

    private final ScheduleFacade scheduleFacade;

    public ScheduleController(ScheduleFacade scheduleFacade) {
        this.scheduleFacade = scheduleFacade;
    }

    @GetMapping("/{leagueId}/schedule")
    public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable String leagueId,
                                                         @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        ScheduleResponse response = scheduleFacade.getSchedule(leagueId, userDetails.getUsername());
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
                MatchScore.of(request.getWinnerScore(), request.getLoserScore()), userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Declara la alineación del partido (jugador del partido, desde el cierre del mercado y hasta revelarse). */
    @PutMapping("/{leagueId}/schedule/matches/{matchId}/lineup")
    public ResponseEntity<Void> declareLineup(@PathVariable String leagueId,
                                              @PathVariable String matchId,
                                              @AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody DeclareLineupRequest request) throws Exception {
        scheduleFacade.declareLineup(leagueId, matchId, request.getPokemonNames(), request.getPasteUrl(),
                userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Replays de Showdown que podrían ser las partidas del partido (jugadores del partido o admin). */
    @GetMapping("/{leagueId}/schedule/matches/{matchId}/replays")
    public ResponseEntity<List<ReplayCandidateResponse>> replayCandidates(@PathVariable String leagueId,
                                                                          @PathVariable String matchId,
                                                                          @RequestParam(required = false) String url,
                                                                          @AuthenticationPrincipal UserDetails userDetails)
            throws Exception {
        return ResponseEntity.ok(scheduleFacade.replayCandidates(leagueId, matchId, url, userDetails.getUsername()));
    }

    /** Registra el resultado con los replays de la serie (jugadores del partido o admin). */
    @PostMapping("/{leagueId}/schedule/matches/{matchId}/import")
    public ResponseEntity<Void> importSeries(@PathVariable String leagueId,
                                             @PathVariable String matchId,
                                             @AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody ImportSeriesRequest request) throws Exception {
        scheduleFacade.importSeries(leagueId, matchId, request.getReplayIds(), userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Corrige el ganador o el marcador de un partido ya registrado (admin). */
    @PutMapping("/{leagueId}/schedule/matches/{matchId}/result")
    public ResponseEntity<Void> correctResult(@PathVariable String leagueId,
                                              @PathVariable String matchId,
                                              @AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody RecordMatchResultRequest request) throws Exception {
        if (request.getWinnerUsername() == null || request.getWinnerUsername().isBlank()) {
            throw new IllegalArgumentException("Indica el ganador.");
        }
        scheduleFacade.correctResult(leagueId, matchId, request.getWinnerUsername(),
                MatchScore.of(request.getWinnerScore(), request.getLoserScore()), userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Deshace el resultado de un partido (admin): vuelve a pendiente y se devuelven las monedas. */
    @DeleteMapping("/{leagueId}/schedule/matches/{matchId}/result")
    public ResponseEntity<Void> revertResult(@PathVariable String leagueId,
                                             @PathVariable String matchId,
                                             @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        scheduleFacade.revertResult(leagueId, matchId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{leagueId}/standings")
    public ResponseEntity<StandingsResponse> getStandings(@PathVariable String leagueId,
                                                           @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(scheduleFacade.getStandings(leagueId, userDetails.getUsername()));
    }

    @GetMapping("/{leagueId}/season-stats")
    public ResponseEntity<SeasonStatsResponse> getSeasonStats(@PathVariable String leagueId,
                                                               @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        return ResponseEntity.ok(scheduleFacade.getSeasonStats(leagueId, userDetails.getUsername()));
    }
}
