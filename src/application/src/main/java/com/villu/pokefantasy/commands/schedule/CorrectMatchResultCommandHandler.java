package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

/**
 * Corrige o deshace un resultado: devuelve las monedas que dio y, si hay nuevo ganador, da las del
 * nuevo resultado. Clasificación y estadísticas se recalculan solas a partir del calendario. Todo en
 * la transacción del comando, así que o se aplica entero o nada.
 */
@Service
public class CorrectMatchResultCommandHandler implements CommandHandler<CorrectMatchResultCommand, Void> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final MatchResultService matchResultService;

    public CorrectMatchResultCommandHandler(ScheduleRepository scheduleRepository,
                                            LeagueRepository leagueRepository,
                                            LeagueAdminGuard leagueAdminGuard,
                                            MatchResultService matchResultService) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.matchResultService = matchResultService;
    }

    @Override
    public Void handle(CorrectMatchResultCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException("No schedule found for league"));
        ScheduleEntity.Match match = matchResultService.findMatch(schedule, command.matchId());

        if (match.getStatus() != MatchStatus.COMPLETED || match.getWinnerUsername() == null) {
            throw new IllegalStateException("Este partido aún no tiene resultado registrado");
        }

        String newWinner = command.newWinnerUsername();
        String newLoser = null;
        if (newWinner != null) {
            newLoser = matchResultService.requireParticipant(match, newWinner);
            if (newWinner.equals(match.getWinnerUsername())) {
                // Mismo ganador: solo puede cambiar el marcador, y eso no mueve monedas.
                if (command.score() == null || command.score().equals(currentScore(match))) {
                    throw new IllegalStateException("'" + newWinner + "' ya figura como ganador de este partido");
                }
                MatchResultService.setScore(match, command.score());
                scheduleRepository.save(schedule);
                return null;
            }
            matchResultService.requireWinnerHasTeam(command.leagueId(), newWinner, newLoser);
        }

        int roundNumber = matchResultService.findRoundNumber(schedule, command.matchId());
        matchResultService.revoke(league, match, roundNumber);
        if (newWinner != null) {
            matchResultService.award(league, match, newWinner, newLoser, command.score(), roundNumber); // guarda la liga
        } else {
            leagueRepository.save(league);
        }
        scheduleRepository.save(schedule);
        return null;
    }

    private static MatchScore currentScore(ScheduleEntity.Match match) {
        return match.getWinnerScore() == null || match.getLoserScore() == null ? null
                : new MatchScore(match.getWinnerScore(), match.getLoserScore());
    }

    @Override
    public Class<CorrectMatchResultCommand> commandType() {
        return CorrectMatchResultCommand.class;
    }
}
