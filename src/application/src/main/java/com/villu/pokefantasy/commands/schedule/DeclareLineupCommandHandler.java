package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.dto.TeamSheet;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.MatchLineup;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Declara (o cambia, mientras el rival no haya enviado la suya) la alineación de un jugador para un partido.
 * Cuando la envía el segundo, las dos quedan reveladas y bloqueadas y se avisa a los dos por push.
 */
@Service
public class DeclareLineupCommandHandler implements CommandHandler<DeclareLineupCommand, Void> {

    static final int MAX_LINEUP = 6;
    private static final DateTimeFormatter OPENS_AT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ScheduleRepository scheduleRepository;
    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;
    private final JornadaWindowService jornadaWindowService;
    private final MatchResultService matchResultService;
    private final UserRepository userRepository;
    private final PushNotificationPort pushNotificationPort;

    public DeclareLineupCommandHandler(ScheduleRepository scheduleRepository, LeagueRepository leagueRepository,
                                       DraftRepository draftRepository, JornadaWindowService jornadaWindowService,
                                       MatchResultService matchResultService, UserRepository userRepository,
                                       PushNotificationPort pushNotificationPort) {
        this.scheduleRepository = scheduleRepository;
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
        this.jornadaWindowService = jornadaWindowService;
        this.matchResultService = matchResultService;
        this.userRepository = userRepository;
        this.pushNotificationPort = pushNotificationPort;
    }

    @Override
    public Void handle(DeclareLineupCommand command) {
        ScheduleEntity schedule = scheduleRepository.findByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException(
                        "Esta liga aún no tiene calendario: se genera al completar el draft."));
        Match match = matchResultService.findMatch(schedule, command.matchId());
        boolean isPlayer1 = command.username().equals(match.getPlayer1());
        if (!isPlayer1 && !command.username().equals(match.getPlayer2())) {
            throw new ForbiddenOperationException("No juegas este partido.");
        }
        LeagueSettings settings = leagueRepository.findById(command.leagueId())
                .map(LeagueEntity::getSettings)
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        Jornada jornada = jornadaOf(schedule, command.matchId());
        boolean active = jornadaWindowService.getActiveJornada(schedule)
                .map(a -> a.getRoundNumber() == jornada.getRoundNumber())
                .orElse(false);
        if (match.getStatus() != MatchStatus.PENDING || !active) {
            throw new IllegalStateException(
                    "Solo se puede declarar la alineación de un partido pendiente de la jornada actual.");
        }
        if (jornada.getStartDate() == null) {
            throw new IllegalStateException("La alineación se declara con las fechas de la temporada configuradas.");
        }
        if (!jornadaWindowService.isLineupPeriodOpen(schedule, jornada, settings)) {
            String opensAt = jornadaWindowService.lineupOpensAt(jornada, settings).orElseThrow().format(OPENS_AT);
            throw new IllegalStateException(
                    "La alineación se puede declarar desde el cierre del mercado (" + opensAt + ").");
        }
        if (match.lineupsRevealed()) {
            throw new IllegalStateException("Las alineaciones ya están reveladas.");
        }

        List<String> names = ownLineup(command);
        boolean open = settings.getTeamSheet() == TeamSheet.OPEN;
        if (open && (command.pasteUrl() == null || !command.pasteUrl().trim().startsWith("https://"))) {
            throw new IllegalArgumentException("Con lista abierta, añade el enlace a tu equipo (https).");
        }
        MatchLineup lineup = new MatchLineup(names,
                open ? command.pasteUrl().trim() : null,
                open && command.pasteWarnings() != null ? command.pasteWarnings() : List.of(),
                Instant.now());
        if (isPlayer1) {
            match.setPlayer1Lineup(lineup);
        } else {
            match.setPlayer2Lineup(lineup);
        }
        scheduleRepository.save(schedule);

        if (match.lineupsRevealed()) {
            notifyRevealed(command, match, jornada.getRoundNumber());
        }
        return null;
    }

    /**
     * Los Pokémon elegidos, con el nombre de su pick: tantos como {@link #MAX_LINEUP} o el equipo entero si es
     * más pequeño, sin repetir y todos del jugador (releído del último draft, no lo que mande el cliente).
     */
    private List<String> ownLineup(DeclareLineupCommand command) {
        Map<String, String> owned = draftRepository.findLatestByLeagueId(command.leagueId())
                .map(draft -> draft.getPicks() == null ? List.<DraftPick>of() : draft.getPicks())
                .orElse(List.of())
                .stream()
                .filter(pick -> command.username().equals(pick.getUsername()))
                .map(DraftPick::getPokemonName)
                .collect(Collectors.toMap(name -> name.toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        int required = Math.min(MAX_LINEUP, owned.size());
        List<String> requested = command.pokemonNames() == null ? List.of() : command.pokemonNames();
        Set<String> distinct = new LinkedHashSet<>();
        requested.forEach(name -> distinct.add(name.toLowerCase(Locale.ROOT)));
        if (requested.size() != required || distinct.size() != required) {
            throw new IllegalArgumentException("Elige " + required + " Pokémon distintos.");
        }
        return requested.stream()
                .map(name -> {
                    String pick = owned.get(name.toLowerCase(Locale.ROOT));
                    if (pick == null) {
                        throw new IllegalArgumentException("'" + name + "' no es de tu equipo.");
                    }
                    return pick;
                })
                .toList();
    }

    private void notifyRevealed(DeclareLineupCommand command, Match match, int roundNumber) {
        notify(command, match.getPlayer1(), match.getPlayer2(), roundNumber);
        notify(command, match.getPlayer2(), match.getPlayer1(), roundNumber);
    }

    private void notify(DeclareLineupCommand command, String player, String rival, int roundNumber) {
        UserEntity user = userRepository.findByUsername(player);
        if (user == null || user.getFcmTokens().isEmpty()) return;
        pushNotificationPort.send(user.getFcmTokens(), PushMessage.lineups(command.leagueId(), command.matchId(),
                "Alineaciones reveladas",
                "Ya puedes ver la alineación de " + rival + " en la jornada " + roundNumber + "."));
    }

    private static Jornada jornadaOf(ScheduleEntity schedule, String matchId) {
        return schedule.getJornadas().stream()
                .filter(j -> j.getMatches() != null && j.getMatches().stream().anyMatch(m -> matchId.equals(m.getId())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Partido no encontrado"));
    }

    @Override
    public Class<DeclareLineupCommand> commandType() {
        return DeclareLineupCommand.class;
    }
}
