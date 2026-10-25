package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

/** Prepara la lista de candidatos de un partido (las reglas están en {@link ReplayImportService}). */
@Service
public class PrepareReplayImportCommandHandler implements CommandHandler<PrepareReplayImportCommand, ReplayImportContext> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;
    private final ReplayImportService replayImportService;

    public PrepareReplayImportCommandHandler(ScheduleRepository scheduleRepository,
                                             LeagueMembershipGuard leagueMembershipGuard,
                                             ReplayImportService replayImportService) {
        this.scheduleRepository = scheduleRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.replayImportService = replayImportService;
    }

    @Override
    public ReplayImportContext handle(PrepareReplayImportCommand command) {
        LeagueEntity league = command.username() == null ? leagueMembershipGuard.requireLeague(command.leagueId())
                : leagueMembershipGuard.requireMember(command.leagueId(), command.username());
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException(
                        "Esta liga aún no tiene calendario: se genera al completar el draft."));
        return replayImportService.contextFor(league, schedule, command.matchId(), command.username());
    }

    @Override
    public Class<PrepareReplayImportCommand> commandType() {
        return PrepareReplayImportCommand.class;
    }
}
