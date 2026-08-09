package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.response.ScheduleResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GetScheduleCommandHandler implements CommandHandler<GetScheduleCommand, ScheduleResponse> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final JornadaWindowService jornadaWindowService;
    private final LeagueMembershipGuard leagueMembershipGuard;

    public GetScheduleCommandHandler(ScheduleRepository scheduleRepository,
                                     LeagueRepository leagueRepository,
                                     JornadaWindowService jornadaWindowService,
                                     LeagueMembershipGuard leagueMembershipGuard) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.jornadaWindowService = jornadaWindowService;
        this.leagueMembershipGuard = leagueMembershipGuard;
    }

    @Override
    public ScheduleResponse handle(GetScheduleCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());

        Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findByLeagueId(command.leagueId());
        if (maybeSchedule.isEmpty()) {
            return null;
        }
        LeagueSettings settings = leagueRepository.findById(command.leagueId())
                .map(LeagueEntity::getSettings)
                .orElse(null);
        return toResponse(maybeSchedule.get(), settings);
    }

    private ScheduleResponse toResponse(ScheduleEntity entity, LeagueSettings settings) {
        List<ScheduleResponse.JornadaResponse> jornadas = entity.getJornadas() == null
                ? List.of()
                : entity.getJornadas().stream()
                        .map(j -> {
                            String stealDeadline = j.getStartDate() != null
                                    ? jornadaWindowService.getStealDeadline(j.getStartDate(), settings).toString() : null;
                            String swapDeadline = j.getStartDate() != null
                                    ? jornadaWindowService.getSwapDeadline(j.getStartDate(), settings).toString() : null;
                            return ScheduleResponse.JornadaResponse.builder()
                                    .roundNumber(j.getRoundNumber())
                                    .startDate(j.getStartDate())
                                    .stealDeadline(stealDeadline)
                                    .swapDeadline(swapDeadline)
                                    .matches(j.getMatches() == null ? List.of() :
                                            j.getMatches().stream()
                                                    .map(m -> ScheduleResponse.MatchResponse.builder()
                                                            .id(m.getId())
                                                            .player1(m.getPlayer1())
                                                            .player2(m.getPlayer2())
                                                            .winnerUsername(m.getWinnerUsername())
                                                            .status(m.getStatus())
                                                            .build())
                                                    .collect(Collectors.toList()))
                                    .build();
                        })
                        .collect(Collectors.toList());

        return ScheduleResponse.builder()
                .leagueId(entity.getLeagueId())
                .jornadas(jornadas)
                .stealWindowOpen(jornadaWindowService.isStealWindowOpen(entity, settings))
                .swapWindowOpen(jornadaWindowService.isSwapWindowOpen(entity, settings))
                .build();
    }

    @Override
    public Class<GetScheduleCommand> commandType() {
        return GetScheduleCommand.class;
    }
}
