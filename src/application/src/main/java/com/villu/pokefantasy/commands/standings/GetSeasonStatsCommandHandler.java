package com.villu.pokefantasy.commands.standings;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.response.SeasonStatsResponse;
import com.villu.pokefantasy.response.SeasonStatsResponse.PlayerSeasonStats;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GetSeasonStatsCommandHandler
        implements CommandHandler<GetSeasonStatsCommand, SeasonStatsResponse> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;

    public GetSeasonStatsCommandHandler(ScheduleRepository scheduleRepository,
                                        LeagueRepository leagueRepository,
                                        DraftRepository draftRepository) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
    }

    @Override
    public SeasonStatsResponse handle(GetSeasonStatsCommand command) {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

        List<String> members = league.getMembers().stream()
                .map(LeagueMember::getUsername)
                .collect(Collectors.toList());

        // Matches completados ordenados por roundNumber ASC
        List<ScheduleEntity.Match> completed = scheduleRepository
                .findByLeagueId(command.leagueId())
                .map(s -> s.getJornadas() == null
                        ? Collections.<ScheduleEntity.Jornada>emptyList()
                        : s.getJornadas())
                .orElse(Collections.emptyList())
                .stream()
                .sorted(Comparator.comparingInt(ScheduleEntity.Jornada::getRoundNumber))
                .flatMap(j -> j.getMatches() == null
                        ? java.util.stream.Stream.empty()
                        : j.getMatches().stream())
                .filter(m -> MatchStatus.COMPLETED.equals(m.getStatus()) && m.getWinnerUsername() != null)
                .collect(Collectors.toList());

        // MVP pokemon: primer pick de draftHistory por jugador (round más bajo)
        Map<String, String> mvpByPlayer = draftRepository
                .findLatestByLeagueId(command.leagueId())
                .map(draft -> draft.getDraftHistory() == null
                        ? Collections.<DraftPick>emptyList()
                        : draft.getDraftHistory())
                .orElse(Collections.emptyList())
                .stream()
                .sorted(Comparator.comparingInt(DraftPick::getRound))
                .collect(Collectors.toMap(
                        DraftPick::getUsername,
                        DraftPick::getPokemonName,
                        (existing, replacement) -> existing  // keep first (lowest round)
                ));

        List<PlayerSeasonStats> stats = members.stream().map(username -> {
            List<ScheduleEntity.Match> myMatches = completed.stream()
                    .filter(m -> username.equals(m.getPlayer1()) || username.equals(m.getPlayer2()))
                    .collect(Collectors.toList());

            int wins   = (int) myMatches.stream().filter(m -> username.equals(m.getWinnerUsername())).count();
            int played = myMatches.size();
            int losses = played - wins;
            int winPct = played == 0 ? 0 : (wins * 100 / played);

            // Streak: recorrer desde el más reciente hacia atrás
            int streak = 0;
            for (int i = myMatches.size() - 1; i >= 0; i--) {
                boolean won = username.equals(myMatches.get(i).getWinnerUsername());
                if (streak == 0) {
                    streak = won ? 1 : -1;
                } else if ((streak > 0) == won) {
                    streak += won ? 1 : -1;
                } else {
                    break;
                }
            }

            return new PlayerSeasonStats(
                    username, wins, losses, played, winPct, streak,
                    mvpByPlayer.get(username)
            );
        })
        .sorted(Comparator.comparingInt(PlayerSeasonStats::wins).reversed())
        .collect(Collectors.toList());

        return new SeasonStatsResponse(stats);
    }

    @Override
    public Class<GetSeasonStatsCommand> commandType() {
        return GetSeasonStatsCommand.class;
    }
}
