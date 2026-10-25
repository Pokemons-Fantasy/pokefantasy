package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.PushMessage;
import com.villu.pokefantasy.dto.TeamSheet;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.MatchLineup;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeclareLineupCommandHandlerTest {

    // Jornada del sábado 6 de junio: el mercado cierra el viernes 5 a las 16:00 (swap, el más tardío).
    private static final String START_DATE = "2026-06-06";
    private static final LocalDateTime OPEN = LocalDateTime.of(2026, 6, 6, 12, 0);
    private static final List<String> ASH_SIX = names("ash", 6);
    private static final List<String> MISTY_SIX = names("misty", 6);

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private UserRepository userRepository;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;

    private ScheduleEntity schedule;
    private Match match;
    private LeagueEntity league;

    private static List<String> names(String owner, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> owner + "mon" + i).toList();
    }

    private static List<DraftPick> picksOf(String owner, List<String> names) {
        return names.stream().map(n -> new DraftPick(owner, n, 1, 1, Instant.EPOCH, null, null)).toList();
    }

    @BeforeEach
    void setUp() {
        match = new Match("m1", "ash", "misty", null, MatchStatus.PENDING);
        schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(new Jornada(1, new ArrayList<>(List.of(match)), START_DATE))));
        lenient().when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        league = new LeagueEntity();
        league.setId("l1");
        league.setSettings(LeagueSettings.defaults());
        lenient().when(leagueRepository.findById("l1")).thenReturn(Optional.of(league));

        DraftEntity draft = new DraftEntity();
        List<DraftPick> picks = new ArrayList<>(picksOf("ash", ASH_SIX));
        picks.addAll(picksOf("misty", MISTY_SIX));
        draft.setPicks(picks);
        lenient().when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));
    }

    private DeclareLineupCommandHandler handlerAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(JornadaWindowService.LEAGUE_ZONE).toInstant(),
                JornadaWindowService.LEAGUE_ZONE);
        return new DeclareLineupCommandHandler(scheduleRepository, leagueRepository, draftRepository,
                new JornadaWindowService(clock),
                new MatchResultService(leagueRepository, draftRepository, activityEventRepository),
                userRepository, pushNotificationPort, leagueMembershipGuard);
    }

    private DeclareLineupCommandHandler handler() {
        return handlerAt(OPEN);
    }

    private static DeclareLineupCommand cmd(String username, List<String> names) {
        return new DeclareLineupCommand("l1", "m1", username, names, null, List.of());
    }

    @Test
    void declare_ok_savesLineupAndDoesNotReveal() {
        handler().handle(cmd("ash", ASH_SIX));

        assertThat(match.getPlayer1Lineup().getPokemonNames()).isEqualTo(ASH_SIX);
        assertThat(match.getPlayer1Lineup().getSubmittedAt()).isNotNull();
        assertThat(match.getPlayer2Lineup()).isNull();
        verify(scheduleRepository).save(schedule);
        verify(pushNotificationPort, never()).send(any(), any());
    }

    @Test
    void declare_secondPlayer_revealsAndPushesBoth() {
        match.setPlayer1Lineup(new MatchLineup(ASH_SIX, null, List.of(), Instant.EPOCH));
        UserEntity ash = new UserEntity();
        ash.setName("ash");
        ash.setFcmTokens(List.of("t-ash"));
        UserEntity misty = new UserEntity();
        misty.setName("misty");
        misty.setFcmTokens(List.of("t-misty"));
        when(userRepository.findByUsername("ash")).thenReturn(ash);
        when(userRepository.findByUsername("misty")).thenReturn(misty);

        handler().handle(cmd("misty", MISTY_SIX));

        assertThat(match.lineupsRevealed()).isTrue();
        verify(pushNotificationPort).send(List.of("t-ash"), PushMessage.lineups("l1", "m1", "Alineaciones reveladas",
                "Ya puedes ver la alineación de misty en la jornada 1."));
        verify(pushNotificationPort).send(List.of("t-misty"), PushMessage.lineups("l1", "m1", "Alineaciones reveladas",
                "Ya puedes ver la alineación de ash en la jornada 1."));
    }

    @Test
    void declare_resend_beforeReveal_overwrites() {
        match.setPlayer1Lineup(new MatchLineup(List.of("old"), null, List.of(), Instant.EPOCH));

        handler().handle(cmd("ash", ASH_SIX));

        assertThat(match.getPlayer1Lineup().getPokemonNames()).isEqualTo(ASH_SIX);
    }

    @Test
    void declare_afterReveal_throwsConflict() {
        match.setPlayer1Lineup(new MatchLineup(ASH_SIX, null, List.of(), Instant.EPOCH));
        match.setPlayer2Lineup(new MatchLineup(MISTY_SIX, null, List.of(), Instant.EPOCH));

        assertThatThrownBy(() -> handler().handle(cmd("ash", ASH_SIX)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Las alineaciones ya están reveladas.");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void declare_beforeMarketClose_throws() {
        assertThatThrownBy(() -> handlerAt(LocalDateTime.of(2026, 6, 5, 15, 0)).handle(cmd("ash", ASH_SIX)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La alineación se puede declarar desde el cierre del mercado (05/06 16:00).");
    }

    @Test
    void declare_jornadaWithoutDate_throws() {
        schedule.getJornadas().get(0).setStartDate(null);

        assertThatThrownBy(() -> handler().handle(cmd("ash", ASH_SIX)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La alineación se declara con las fechas de la temporada configuradas.");
    }

    @Test
    void declare_notActiveJornada_throws() {
        Match previous = new Match("m0", "brock", "gary", null, MatchStatus.PENDING);
        schedule.getJornadas().add(0, new Jornada(0, new ArrayList<>(List.of(previous)), "2026-05-30"));

        assertThatThrownBy(() -> handler().handle(cmd("ash", ASH_SIX)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Solo se puede declarar la alineación de un partido pendiente de la jornada actual.");
    }

    @Test
    void declare_completedMatch_throws() {
        match.setStatus(MatchStatus.COMPLETED);

        assertThatThrownBy(() -> handler().handle(cmd("ash", ASH_SIX)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Solo se puede declarar la alineación de un partido pendiente de la jornada actual.");
    }

    @Test
    void declare_notPlayer_throws() {
        assertThatThrownBy(() -> handler().handle(cmd("brock", ASH_SIX)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("No juegas este partido.");
    }

    @Test
    void declare_wrongCount_throws() {
        List<String> repeated = new ArrayList<>(ASH_SIX.subList(0, 5));
        repeated.add(ASH_SIX.get(0));

        assertThatThrownBy(() -> handler().handle(cmd("ash", ASH_SIX.subList(0, 5))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elige 6 Pokémon distintos.");
        assertThatThrownBy(() -> handler().handle(cmd("ash", repeated)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elige 6 Pokémon distintos.");
    }

    @Test
    void declare_teamOfFour_requiresFour() {
        DraftEntity small = new DraftEntity();
        small.setPicks(new ArrayList<>(picksOf("ash", ASH_SIX.subList(0, 4))));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(small));

        handler().handle(cmd("ash", ASH_SIX.subList(0, 4)));

        assertThat(match.getPlayer1Lineup().getPokemonNames()).hasSize(4);
    }

    @Test
    void declare_notOwnPokemon_throws() {
        List<String> withRival = new ArrayList<>(ASH_SIX.subList(0, 5));
        withRival.add(MISTY_SIX.get(0));

        assertThatThrownBy(() -> handler().handle(cmd("ash", withRival)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'mistymon1' no es de tu equipo.");
    }

    @Test
    void declare_ownPokemon_caseInsensitive() {
        handler().handle(cmd("ash", ASH_SIX.stream().map(String::toUpperCase).toList()));

        assertThat(match.getPlayer1Lineup()).isNotNull();
    }

    @Test
    void declare_openSheetWithoutHttpsUrl_throws() {
        league.getSettings().setTeamSheet(TeamSheet.OPEN);

        assertThatThrownBy(() -> handler().handle(
                new DeclareLineupCommand("l1", "m1", "ash", ASH_SIX, "http://pokepast.es/x", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Con lista abierta, añade el enlace a tu equipo (https).");
    }

    @Test
    void declare_openSheet_keepsUrlAndWarnings() {
        league.getSettings().setTeamSheet(TeamSheet.OPEN);

        handler().handle(new DeclareLineupCommand("l1", "m1", "ash", ASH_SIX, "https://pokepast.es/abc",
                List.of("No se pudo leer el paste.")));

        assertThat(match.getPlayer1Lineup().getPasteUrl()).isEqualTo("https://pokepast.es/abc");
        assertThat(match.getPlayer1Lineup().getPasteWarnings()).containsExactly("No se pudo leer el paste.");
    }

    @Test
    void declare_closedSheet_ignoresPasteUrl() {
        handler().handle(new DeclareLineupCommand("l1", "m1", "ash", ASH_SIX, "https://pokepast.es/abc",
                List.of("En el paste falta x.")));

        assertThat(match.getPlayer1Lineup().getPasteUrl()).isNull();
        assertThat(match.getPlayer1Lineup().getPasteWarnings()).isEmpty();
    }

    @Test
    void declare_noLongerMember_throws() {
        when(leagueMembershipGuard.requireMember("l1", "ash"))
                .thenThrow(new ForbiddenOperationException("No eres miembro de esta liga"));

        assertThatThrownBy(() -> handler().handle(cmd("ash", ASH_SIX)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void declare_withoutPokemon_throws() {
        DraftEntity empty = new DraftEntity();
        empty.setPicks(new ArrayList<>(picksOf("misty", MISTY_SIX)));
        when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(empty));

        assertThatThrownBy(() -> handler().handle(cmd("ash", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes Pokémon para declarar.");
    }

    @Test
    void declare_leagueWithoutSettings_usesDefaults() {
        league.setSettings(null);

        handler().handle(cmd("ash", ASH_SIX));

        assertThat(match.getPlayer1Lineup().getPasteUrl()).isNull();
    }

    @Test
    void declare_pasteUrlTooLong_throws() {
        league.getSettings().setTeamSheet(TeamSheet.OPEN);

        assertThatThrownBy(() -> handler().handle(new DeclareLineupCommand("l1", "m1", "ash", ASH_SIX,
                "https://pokepast.es/" + "a".repeat(200), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El enlace a tu equipo es demasiado largo.");
    }

    @Test
    void commandType() {
        assertThat(handler().commandType()).isEqualTo(DeclareLineupCommand.class);
    }
}
