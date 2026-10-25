package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.AutoImportTarget;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Partidos pendientes de la jornada activa de cada liga, con fecha y el mercado ya cerrado, y los dos
 * jugadores con Showdown vinculado: los que el import automático intenta registrar.
 */
@Service
public class ListAutoImportTargetsCommandHandler implements CommandHandler<ListAutoImportTargetsCommand, List<AutoImportTarget>> {

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final UserRepository userRepository;
    private final JornadaWindowService jornadaWindowService;

    public ListAutoImportTargetsCommandHandler(ScheduleRepository scheduleRepository, LeagueRepository leagueRepository,
                                               UserRepository userRepository, JornadaWindowService jornadaWindowService) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.userRepository = userRepository;
        this.jornadaWindowService = jornadaWindowService;
    }

    @Override
    public List<AutoImportTarget> handle(ListAutoImportTargetsCommand command) {
        List<AutoImportTarget> targets = new ArrayList<>();
        for (ScheduleEntity schedule : scheduleRepository.findAll()) {
            LeagueEntity league = leagueRepository.findById(schedule.getLeagueId()).orElse(null);
            Jornada jornada = jornadaWindowService.getActiveJornada(schedule).orElse(null);
            // Sin fecha no hay semana que acote los replays (un amistoso antiguo valdría): esas, solo a mano.
            if (league == null || jornada == null
                    || !jornadaWindowService.isLineupPeriodOpen(schedule, jornada, league.getSettings())) {
                continue;
            }
            List<Match> pending = jornada.getMatches().stream().filter(m -> m.getStatus() != MatchStatus.COMPLETED).toList();
            // findByUsernames solo trae nombre, avatar y showdownName (se fija junto a showdownId al vincular).
            Set<String> linked = userRepository.findByUsernames(pending.stream()
                            .flatMap(m -> Stream.of(m.getPlayer1(), m.getPlayer2())).collect(Collectors.toSet()))
                    .stream().filter(u -> u.getShowdownName() != null).map(UserEntity::getName).collect(Collectors.toSet());
            pending.stream()
                    .filter(m -> linked.contains(m.getPlayer1()) && linked.contains(m.getPlayer2()))
                    .forEach(m -> targets.add(new AutoImportTarget(league.getId(), m.getId())));
        }
        return targets;
    }

    @Override
    public Class<ListAutoImportTargetsCommand> commandType() {
        return ListAutoImportTargetsCommand.class;
    }
}
