package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.dto.BattleGameType;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
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
 * Reglas comunes a listar candidatos e importar una serie de replays: quién puede (jugadores del partido o un
 * admin), partido pendiente, las dos cuentas de Showdown vinculadas, formato, semana y replays ya usados.
 */
@Service
public class ReplayImportService {

    private final UserRepository userRepository;
    private final MatchResultService matchResultService;
    private final JornadaWindowService jornadaWindowService;

    public ReplayImportService(UserRepository userRepository, MatchResultService matchResultService,
                               JornadaWindowService jornadaWindowService) {
        this.userRepository = userRepository;
        this.matchResultService = matchResultService;
        this.jornadaWindowService = jornadaWindowService;
    }

    /**
     * Valida y arma el contexto del partido con datos ya leídos en la transacción de quien llama.
     * {@code username == null}: lo pide el sistema (import automático) y no se comprueba quién.
     */
    public ReplayImportContext contextFor(LeagueEntity league, ScheduleEntity schedule, String matchId, String username) {
        Match match = matchResultService.findMatch(schedule, matchId);
        boolean player = match.getPlayer1().equals(username) || match.getPlayer2().equals(username);
        boolean admin = league.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username) && m.getLeagueRole() == LeagueRole.ADMIN);
        if (username != null && !player && !admin) {
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
        Long marketClosedEpoch = jornadaWindowService.lineupOpensAt(jornada, settings)
                .map(t -> t.atZone(JornadaWindowService.LEAGUE_ZONE).toEpochSecond()).orElse(null);

        Set<String> used = new HashSet<>();
        schedule.getJornadas().forEach(j -> j.getMatches().stream()
                .filter(m -> !Objects.equals(m.getId(), match.getId()) && m.getReplayIds() != null)
                .forEach(m -> used.addAll(m.getReplayIds())));

        return new ReplayImportContext(match.getPlayer1(), match.getPlayer2(), id1, id2,
                singles ? "gen9customgame" : "gen9doublescustomgame", fromEpoch, used,
                match.getPlayer1Lineup(), match.getPlayer2Lineup(), bestOf, marketClosedEpoch);
    }

    private String showdownIdOf(String username) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null || user.getShowdownId() == null) {
            throw new IllegalArgumentException(username + " no ha vinculado su cuenta de Showdown.");
        }
        return user.getShowdownId();
    }
}
