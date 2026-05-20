package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.response.ScheduleResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GetScheduleCommandHandler implements CommandHandler<GetScheduleCommand, ScheduleResponse> {

    private final ScheduleRepository scheduleRepository;

    public GetScheduleCommandHandler(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    public ScheduleResponse handle(GetScheduleCommand command) {
        Optional<ScheduleEntity> maybeSchedule = scheduleRepository.findByLeagueId(command.leagueId());
        if (maybeSchedule.isEmpty()) {
            return null;
        }
        return toResponse(maybeSchedule.get());
    }

    private ScheduleResponse toResponse(ScheduleEntity entity) {
        List<ScheduleResponse.JornadaResponse> jornadas = entity.getJornadas() == null
                ? List.of()
                : entity.getJornadas().stream()
                        .map(j -> ScheduleResponse.JornadaResponse.builder()
                                .roundNumber(j.getRoundNumber())
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
                                .build())
                        .collect(Collectors.toList());

        return ScheduleResponse.builder()
                .leagueId(entity.getLeagueId())
                .jornadas(jornadas)
                .build();
    }

    @Override
    public Class<GetScheduleCommand> commandType() {
        return GetScheduleCommand.class;
    }
}
