package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RecordMatchResultCommandHandler implements CommandHandler<RecordMatchResultCommand, Void> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public RecordMatchResultCommandHandler(ScheduleRepository scheduleRepository,
                                           LeagueAdminGuard leagueAdminGuard) {
        this.scheduleRepository = scheduleRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public Void handle(RecordMatchResultCommand command) {
        leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException(
                        "No schedule found for league. Generate the schedule first by completing the draft."));

        ScheduleEntity.Match match = findMatch(schedule, command.matchId());

        if (!command.winnerUsername().equals(match.getPlayer1())
                && !command.winnerUsername().equals(match.getPlayer2())) {
            throw new IllegalArgumentException(
                    "Winner '" + command.winnerUsername() + "' is not a participant of this match");
        }

        match.setWinnerUsername(command.winnerUsername());
        match.setStatus(MatchStatus.COMPLETED);
        scheduleRepository.save(schedule);
        return null;
    }

    private ScheduleEntity.Match findMatch(ScheduleEntity schedule, String matchId) {
        if (schedule.getJornadas() == null) {
            throw new IllegalArgumentException("Match not found: " + matchId);
        }
        for (ScheduleEntity.Jornada jornada : schedule.getJornadas()) {
            if (jornada.getMatches() == null) continue;
            for (ScheduleEntity.Match m : jornada.getMatches()) {
                if (matchId.equals(m.getId())) {
                    return m;
                }
            }
        }
        throw new IllegalArgumentException("Match not found: " + matchId);
    }

    @Override
    public Class<RecordMatchResultCommand> commandType() {
        return RecordMatchResultCommand.class;
    }
}
