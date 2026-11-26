package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.league.LeaguePushTokens;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Aplaza (o adelanta) en cascada: la jornada {@code fromRound} y todas las siguientes se mueven {@code weeks}
 * semanas. Si entre ellas está la jornada activa, su mercado se reabre solo (los cierres salen de la fecha) y se
 * borran las alineaciones de sus partidos pendientes, porque los equipos pueden volver a cambiar.
 */
@Service
public class ShiftScheduleCommandHandler implements CommandHandler<ShiftScheduleCommand, Void> {

    static final int MAX_WEEKS = 12;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final JornadaWindowService jornadaWindowService;
    private final ActivityEventRepository activityEventRepository;
    private final LeaguePushTokens leaguePushTokens;
    private final PushNotificationPort pushNotificationPort;

    public ShiftScheduleCommandHandler(ScheduleRepository scheduleRepository, LeagueRepository leagueRepository,
                                       LeagueAdminGuard leagueAdminGuard, JornadaWindowService jornadaWindowService,
                                       ActivityEventRepository activityEventRepository,
                                       LeaguePushTokens leaguePushTokens, PushNotificationPort pushNotificationPort) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.jornadaWindowService = jornadaWindowService;
        this.activityEventRepository = activityEventRepository;
        this.leaguePushTokens = leaguePushTokens;
        this.pushNotificationPort = pushNotificationPort;
    }

    @Override
    public Void handle(ShiftScheduleCommand command) {
        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());
        Integer weeks = command.weeks();
        if (command.fromRound() == null || weeks == null || weeks == 0 || Math.abs(weeks) > MAX_WEEKS) {
            throw new IllegalArgumentException("Indica entre 1 y " + MAX_WEEKS + " semanas (en negativo para adelantar).");
        }
        int fromRound = command.fromRound();
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .filter(s -> s.getJornadas() != null)
                .orElseThrow(() -> new IllegalStateException(
                        "Esta liga aún no tiene calendario: se genera al completar el draft."));
        Jornada from = jornada(schedule, fromRound)
                .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada."));
        if (schedule.getJornadas().stream().anyMatch(j -> j.getStartDate() == null)) {
            throw new IllegalStateException("Configura antes la fecha de inicio de la temporada.");
        }
        if (!jornadaWindowService.isShiftable(schedule, from)) {
            throw new IllegalStateException(
                    "Solo se puede mover la jornada actual si no tiene resultados, o las siguientes.");
        }
        LocalDate newFrom = LocalDate.parse(from.getStartDate()).plusWeeks(weeks);
        Optional<Jornada> previous = jornada(schedule, fromRound - 1);
        if (previous.isPresent() && !monday(newFrom).isAfter(monday(LocalDate.parse(previous.get().getStartDate())))) {
            throw new IllegalArgumentException("La jornada " + fromRound + " no puede caer en la semana de la jornada "
                    + (fromRound - 1) + " ni antes.");
        }
        if (monday(newFrom).isBefore(monday(jornadaWindowService.now().toLocalDate()))) {
            throw new IllegalArgumentException("No se puede mover una jornada a una semana ya pasada.");
        }

        Optional<Integer> activeRound = jornadaWindowService.getActiveJornada(schedule).map(Jornada::getRoundNumber);
        schedule.getJornadas().stream()
                .filter(j -> j.getRoundNumber() >= fromRound)
                .forEach(j -> j.setStartDate(LocalDate.parse(j.getStartDate()).plusWeeks(weeks).toString()));
        Set<String> lostLineup = activeRound.filter(round -> round >= fromRound)
                .flatMap(round -> jornada(schedule, round))
                .map(ShiftScheduleCommandHandler::clearLineups)
                .orElse(Set.of());

        if (fromRound == 1) {
            if (league.getSettings() == null) {
                league.setSettings(LeagueSettings.defaults());
            }
            league.getSettings().setSeasonStartDate(newFrom.toString());
            leagueRepository.save(league);
        }
        activityEventRepository.save(ActivityEventEntity.builder()
                .leagueId(league.getId())
                .type(ActivityEventType.SCHEDULE_SHIFTED)
                .actorUsername(command.requestingUsername())
                .roundNumber(fromRound)
                .weeks(weeks)
                .createdAt(Instant.now())
                .build());
        pushNotificationPort.send(leaguePushTokens.ofMembersExcept(league, command.requestingUsername()),
                PushMessage.schedule(league.getId(),
                        weeks > 0 ? "Calendario aplazado" : "Calendario adelantado",
                        league.getName() + ": la jornada " + fromRound + " pasa al fin de semana del "
                                + DAY.format(newFrom) + "."));
        if (!lostLineup.isEmpty()) {
            Jornada active = jornada(schedule, activeRound.orElseThrow()).orElseThrow();
            // Adelantar a la semana en curso puede dejar los cierres ya pasados: entonces no se reabre nada.
            boolean marketReopens = jornadaWindowService.lineupOpensAt(active, league.getSettings())
                    .map(opens -> jornadaWindowService.now().isBefore(opens)).orElse(false);
            String body = "Se ha movido la jornada " + active.getRoundNumber() + (marketReopens
                    ? " y se reabre el mercado: vuelve a declarar tu alineación cuando cierre."
                    : ": vuelve a declarar tu alineación.");
            pushNotificationPort.send(leaguePushTokens.ofUsers(lostLineup),
                    PushMessage.schedule(league.getId(), "Alineación anulada", body));
        }
        scheduleRepository.save(schedule);
        return null;
    }

    /** Borra las alineaciones de los partidos pendientes; devuelve quién tenía una. */
    private static Set<String> clearLineups(Jornada jornada) {
        Set<String> players = new LinkedHashSet<>();
        for (Match match : jornada.getMatches()) {
            if (match.getStatus() != MatchStatus.PENDING) continue;
            if (match.getPlayer1Lineup() != null) players.add(match.getPlayer1());
            if (match.getPlayer2Lineup() != null) players.add(match.getPlayer2());
            match.setPlayer1Lineup(null);
            match.setPlayer2Lineup(null);
        }
        return players;
    }

    private static Optional<Jornada> jornada(ScheduleEntity schedule, int round) {
        return schedule.getJornadas().stream().filter(j -> j.getRoundNumber() == round).findFirst();
    }

    private static LocalDate monday(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    @Override
    public Class<ShiftScheduleCommand> commandType() {
        return ShiftScheduleCommand.class;
    }
}
