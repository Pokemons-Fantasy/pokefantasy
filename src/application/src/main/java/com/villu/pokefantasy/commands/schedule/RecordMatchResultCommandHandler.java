package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

@Service
public class RecordMatchResultCommandHandler implements CommandHandler<RecordMatchResultCommand, Void> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final MatchResultService matchResultService;

    public RecordMatchResultCommandHandler(ScheduleRepository scheduleRepository,
                                           LeagueAdminGuard leagueAdminGuard,
                                           MatchResultService matchResultService) {
        this.scheduleRepository = scheduleRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.matchResultService = matchResultService;
    }

    @Override
    public Void handle(RecordMatchResultCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException(
                        "No schedule found for league. Generate the schedule first by completing the draft."));

        ScheduleEntity.Match match = matchResultService.findMatch(schedule, command.matchId());

        if (match.getStatus() == MatchStatus.COMPLETED) {
            throw new IllegalStateException(
                    "El resultado de este partido ya fue registrado. Para cambiarlo, corrígelo o deshazlo.");
        }

        String loserUsername = matchResultService.requireParticipant(match, command.winnerUsername());
        matchResultService.requireWinnerHasTeam(command.leagueId(), command.winnerUsername(), loserUsername);

        int roundNumber = matchResultService.findRoundNumber(schedule, command.matchId());
        matchResultService.award(league, match, command.winnerUsername(), loserUsername, roundNumber);
        scheduleRepository.save(schedule);
        return null;
    }

    @Override
    public Class<RecordMatchResultCommand> commandType() {
        return RecordMatchResultCommand.class;
    }
}
