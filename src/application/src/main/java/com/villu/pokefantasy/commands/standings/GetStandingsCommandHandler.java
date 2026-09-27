package com.villu.pokefantasy.commands.standings;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.response.PlayerStandingResponse;
import com.villu.pokefantasy.response.StandingsResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GetStandingsCommandHandler implements CommandHandler<GetStandingsCommand, StandingsResponse> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;

    public GetStandingsCommandHandler(ScheduleRepository scheduleRepository,
                                      LeagueRepository leagueRepository,
                                      LeagueMembershipGuard leagueMembershipGuard) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
    }

    @Override
    public StandingsResponse handle(GetStandingsCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());

        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada: " + command.leagueId()));

        // [victorias, derrotas, marcador a favor, marcador en contra]
        Map<String, int[]> stats = new HashMap<>();
        for (LeagueMember member : league.getMembers()) {
            stats.put(member.getUsername(), new int[4]);
        }

        Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findByLeagueId(command.leagueId());
        maybeSchedule.ifPresent(schedule -> {
            if (schedule.getJornadas() == null) return;
            schedule.getJornadas().stream()
                    .filter(j -> j.getMatches() != null)
                    .flatMap(j -> j.getMatches().stream())
                    .filter(m -> m.getStatus() == MatchStatus.COMPLETED && m.getWinnerUsername() != null)
                    .forEach(m -> {
                        String winner = m.getWinnerUsername();
                        String loser = m.getPlayer1().equals(winner) ? m.getPlayer2() : m.getPlayer1();
                        int[] w = stats.computeIfAbsent(winner, k -> new int[4]);
                        int[] l = stats.computeIfAbsent(loser, k -> new int[4]);
                        w[0]++;
                        l[1]++;
                        if (m.getWinnerScore() != null && m.getLoserScore() != null) {
                            w[2] += m.getWinnerScore();
                            w[3] += m.getLoserScore();
                            l[2] += m.getLoserScore();
                            l[3] += m.getWinnerScore();
                        }
                    });
        });

        Map<String, Integer> coinMap = new HashMap<>();
        for (LeagueMember member : league.getMembers()) {
            coinMap.put(member.getUsername(), member.getCoinBalance());
        }

        // Orden: victorias, diferencia de marcador, monedas y nombre.
        List<PlayerStandingResponse> standings = stats.entrySet().stream()
                .map(e -> PlayerStandingResponse.builder()
                        .username(e.getKey())
                        .wins(e.getValue()[0])
                        .losses(e.getValue()[1])
                        .played(e.getValue()[0] + e.getValue()[1])
                        .coins(coinMap.getOrDefault(e.getKey(), 0))
                        .scoreFor(e.getValue()[2])
                        .scoreAgainst(e.getValue()[3])
                        .scoreDiff(e.getValue()[2] - e.getValue()[3])
                        .build())
                .sorted(Comparator.comparingInt(PlayerStandingResponse::getWins).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerStandingResponse::getScoreDiff).reversed())
                        .thenComparing(Comparator.comparingInt(PlayerStandingResponse::getCoins).reversed())
                        .thenComparing(PlayerStandingResponse::getUsername))
                .toList();

        return StandingsResponse.builder().standings(standings).build();
    }

    @Override
    public Class<GetStandingsCommand> commandType() {
        return GetStandingsCommand.class;
    }
}
