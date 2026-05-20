package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

@Service
public class RecordMatchResultCommandHandler implements CommandHandler<RecordMatchResultCommand, Void> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final LeagueRepository leagueRepository;

    public RecordMatchResultCommandHandler(ScheduleRepository scheduleRepository,
                                           LeagueAdminGuard leagueAdminGuard,
                                           LeagueRepository leagueRepository) {
        this.scheduleRepository = scheduleRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.leagueRepository = leagueRepository;
    }

    @Override
    public Void handle(RecordMatchResultCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

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

        String loser = command.winnerUsername().equals(match.getPlayer1())
                ? match.getPlayer2()
                : match.getPlayer1();
        distributeCoins(league, command.winnerUsername(), loser);

        return null;
    }

    private void distributeCoins(LeagueEntity league, String winner, String loser) {
        LeagueSettings settings = league.getSettings();
        int coinsWin  = (settings != null && settings.getCoinsPerWin()  != null) ? settings.getCoinsPerWin()  : 0;
        int coinsLoss = (settings != null && settings.getCoinsPerLoss() != null) ? settings.getCoinsPerLoss() : 0;

        for (LeagueMember m : league.getMembers()) {
            if (m.getUsername().equals(winner)) m.setCoinBalance(m.getCoinBalance() + coinsWin);
            if (m.getUsername().equals(loser))  m.setCoinBalance(m.getCoinBalance() + coinsLoss);
        }
        leagueRepository.save(league);
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
