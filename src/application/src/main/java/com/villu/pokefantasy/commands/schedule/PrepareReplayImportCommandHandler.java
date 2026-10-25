package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.dto.BattleGameType;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Prepara el import de replays de un partido: solo sus jugadores o un admin, con el partido pendiente y las dos
 * cuentas de Showdown vinculadas. Lo usan la lista de candidatos y el propio import ({@link ImportSeriesCommandHandler}).
 */
@Service
public class PrepareReplayImportCommandHandler implements CommandHandler<PrepareReplayImportCommand, ReplayImportContext> {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;
    private final MatchResultService matchResultService;

    public PrepareReplayImportCommandHandler(ScheduleRepository scheduleRepository, UserRepository userRepository,
                                             LeagueMembershipGuard leagueMembershipGuard,
                                             MatchResultService matchResultService) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
        this.matchResultService = matchResultService;
    }

    @Override
    public ReplayImportContext handle(PrepareReplayImportCommand command) {
        LeagueEntity league = leagueMembershipGuard.requireMember(command.leagueId(), command.username());
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException(
                        "Esta liga aún no tiene calendario: se genera al completar el draft."));
        return contextFor(league, schedule, command.matchId(), command.username());
    }

    /** Valida y arma el contexto con datos ya leídos (también desde el handler del import, en su transacción). */
    ReplayImportContext contextFor(LeagueEntity league, ScheduleEntity schedule, String matchId, String username) {
        Match match = matchResultService.findMatch(schedule, matchId);
        boolean player = username.equals(match.getPlayer1()) || username.equals(match.getPlayer2());
        boolean admin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username) && m.getLeagueRole() == LeagueRole.ADMIN);
        if (!player && !admin) {
            throw new ForbiddenOperationException("Solo los jugadores del partido o un admin pueden importar el resultado.");
        }
        if (match.getStatus() == MatchStatus.COMPLETED) {
            throw new IllegalStateException("El resultado de este partido ya se ha registrado.");
        }
        String id1 = showdownIdOf(match.getPlayer1());
        String id2 = showdownIdOf(match.getPlayer2());

        LeagueSettings settings = league.getSettings();
        boolean singles = settings != null && settings.getBattleGameType() == BattleGameType.SINGLES;
        int bestOf = settings != null && settings.getBestOf() != null ? settings.getBestOf() : LeagueSettings.DEFAULT_BEST_OF;

        Jornada jornada = schedule.getJornadas().stream()
                .filter(j -> j.getMatches() != null && j.getMatches().contains(match))
                .findFirst().orElseThrow();
        Long fromEpoch = jornada.getStartDate() == null ? null
                : LocalDate.parse(jornada.getStartDate()).with(DayOfWeek.MONDAY)
                        .atStartOfDay(JornadaWindowService.LEAGUE_ZONE).toEpochSecond();

        Set<String> used = new HashSet<>();
        schedule.getJornadas().forEach(j -> j.getMatches().stream()
                .filter(m -> !Objects.equals(m.getId(), match.getId()) && m.getReplayIds() != null)
                .forEach(m -> used.addAll(m.getReplayIds())));

        return new ReplayImportContext(match.getPlayer1(), match.getPlayer2(), id1, id2,
                singles ? "gen9customgame" : "gen9doublescustomgame", fromEpoch, used,
                match.getPlayer1Lineup(), match.getPlayer2Lineup(), bestOf);
    }

    private String showdownIdOf(String username) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null || user.getShowdownId() == null) {
            throw new IllegalArgumentException(username + " no ha vinculado su cuenta de Showdown.");
        }
        return user.getShowdownId();
    }

    @Override
    public Class<PrepareReplayImportCommand> commandType() {
        return PrepareReplayImportCommand.class;
    }
}
