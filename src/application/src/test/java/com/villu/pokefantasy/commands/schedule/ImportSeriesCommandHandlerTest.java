package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.ports.ShowdownReplay;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImportSeriesCommandHandlerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;
    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private JornadaCompletionNotifier jornadaCompletionNotifier;

    private Match match;
    private Match other;
    private LeagueEntity league;
    private ImportSeriesCommandHandler handler;

    private static UserEntity user(String name, String showdownId) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setShowdownId(showdownId);
        return u;
    }

    @BeforeEach
    void setUp() {
        match = new Match("m1", "ash", "misty", null, MatchStatus.PENDING);
        other = new Match("m2", "brock", "gary", null, MatchStatus.PENDING);
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(new Jornada(1, new ArrayList<>(List.of(match, other)), null))));
        lenient().when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        league = new LeagueEntity();
        league.setId("l1");
        league.setSettings(LeagueSettings.defaults());
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.USER, 0),
                new LeagueMember("misty", LeagueRole.USER, 0))));
        lenient().when(leagueMembershipGuard.requireMember("l1", "ash")).thenReturn(league);
        lenient().when(userRepository.findByUsername("ash")).thenReturn(user("ash", "ashketchum"));
        lenient().when(userRepository.findByUsername("misty")).thenReturn(user("misty", "mistyw"));

        DraftEntity draft = new DraftEntity();
        draft.setPicks(List.of(new DraftPick("ash", "pikachu", 25, 1, Instant.EPOCH, null, null),
                new DraftPick("misty", "starmie", 121, 1, Instant.EPOCH, null, null)));
        lenient().when(draftRepository.findLatestByLeagueId("l1")).thenReturn(Optional.of(draft));

        MatchResultService results = new MatchResultService(leagueRepository, draftRepository, activityEventRepository);
        handler = new ImportSeriesCommandHandler(scheduleRepository, leagueMembershipGuard,
                new ReplayImportService(userRepository, results, new JornadaWindowService()), results,
                jornadaCompletionNotifier);
    }

    /** Partida de una serie; {@code swap} pone a Misty en p1 (el lado cambia entre partidas). */
    private static ShowdownReplay game(String id, int number, String winner, boolean swap) {
        String p1 = swap ? "Misty W" : "Ash Ketchum";
        String p2 = swap ? "Ash Ketchum" : "Misty W";
        String log = "|uhtml|bestof|<h2><strong>Game " + number + "</strong> of <a href=\"/game-bestof3-"
                + "gen9doublescustomgame-77\">a best-of-3</a></h2>\n|player|p1|" + p1 + "|a|\n|player|p2|" + p2
                + "|b|\n|poke|p1|Pikachu, L50|\n|win|" + winner;
        return new ShowdownReplay(id, "gen9doublescustomgame", List.of(p1, p2), log, 1_800_000_000L + number);
    }

    private void importReplays(ShowdownReplay... replays) {
        handler.handle(new ImportSeriesCommand("l1", "m1", "ash", List.of(replays)));
    }

    @Test
    void bestOf3_twoOne_registersWinnerScoreCoinsAndReplays() {
        importReplays(game("g2", 2, "Misty W", true), game("g1", 1, "Ash Ketchum", false),
                game("g3", 3, "ASH KETCHUM", true));

        assertThat(match.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(match.getWinnerUsername()).isEqualTo("ash");
        assertThat(match.getWinnerScore()).isEqualTo(2);
        assertThat(match.getLoserScore()).isEqualTo(1);
        assertThat(match.getReplayIds()).containsExactly("g1", "g2", "g3");
        assertThat(league.getMembers().getFirst().getCoinBalance()).isEqualTo(100);
        verify(scheduleRepository).save(any());
        verify(jornadaCompletionNotifier).notifyIfCompleted(league, scheduleRepository.findByLeagueId("l1").orElseThrow(), 1);
    }

    @Test
    void system_importsWithoutWhoCheck() {
        org.mockito.Mockito.when(leagueMembershipGuard.requireLeague("l1")).thenReturn(league);

        handler.handle(new ImportSeriesCommand("l1", "m1", null,
                List.of(game("g1", 1, "Misty W", false), game("g2", 2, "Misty W", true))));

        assertThat(match.getWinnerUsername()).isEqualTo("misty");
        assertThat(match.getReplayIds()).containsExactly("g1", "g2");
    }

    @Test
    void bestOf3_twoZero() {
        importReplays(game("g1", 1, "Misty W", false), game("g2", 2, "Misty W", true));

        assertThat(match.getWinnerUsername()).isEqualTo("misty");
        assertThat(match.getWinnerScore()).isEqualTo(2);
        assertThat(match.getLoserScore()).isEqualTo(0);
    }

    @Test
    void bestOf1_oneReplayNoScore() {
        league.getSettings().setBestOf(1);

        importReplays(game("g1", 1, "Ash Ketchum", false));

        assertThat(match.getWinnerUsername()).isEqualTo("ash");
        assertThat(match.getWinnerScore()).isNull();
    }

    @Test
    void invalidSeries_rejected() {
        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false), game("g2", 2, "Misty W", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Para una serie al mejor de 3 marca 2 partidas del mismo ganador o 3 con un 2-1.");
        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false)))
                .isInstanceOf(IllegalArgumentException.class);
        league.getSettings().setBestOf(1);
        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false), game("g2", 2, "Ash Ketchum", true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Marca una partida.");
        verify(scheduleRepository, never()).save(any());
    }

    /** Partida de otra serie ({@code gen9doublescustomgame-88}). */
    private static ShowdownReplay otherSeriesGame(String id, int number, String winner) {
        ShowdownReplay base = game(id, number, winner, false);
        return new ShowdownReplay(id, base.formatid(), base.players(), base.log().replace("-77\"", "-88\""),
                base.uploadtime());
    }

    @Test
    void gamesFromDifferentSeries_rejected() {
        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false),
                otherSeriesGame("h1", 1, "Ash Ketchum")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Marca partidas de una misma serie.");
    }

    @Test
    void skippedGameOfTheSeries_rejected() {
        // A-B-A: marcar solo la 1 y la 3 daría un 2-0 falso
        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false), game("g3", 3, "Ash Ketchum", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Marca las partidas seguidas de la serie, empezando por la 1.");
    }

    @Test
    void bestOf1_gameOfASeries_rejected() {
        league.getSettings().setBestOf(1);

        assertThatThrownBy(() -> importReplays(game("g2", 2, "Ash Ketchum", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Marca las partidas seguidas de la serie, empezando por la 1.");
    }

    @Test
    void repeatedReplay_rejected() {
        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false), game("g1", 1, "Ash Ketchum", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No marques la misma partida dos veces.");
    }

    @Test
    void replayNotSelectable_rejectedWithReason() {
        other.setReplayIds(List.of("g1"));

        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false), game("g2", 2, "Ash Ketchum", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("g1: Ya usado en otro partido.");
    }

    @Test
    void privateReplayNotSelectable_errorHidesItsPassword() {
        other.setReplayIds(List.of("g1-secretpw"));

        assertThatThrownBy(() -> importReplays(game("g1-secretpw", 1, "Ash Ketchum", false),
                game("g2", 2, "Ash Ketchum", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("g1-***pw: Ya usado en otro partido.");
    }

    @Test
    void alreadyCompleted_conflict() {
        match.setStatus(MatchStatus.COMPLETED);

        assertThatThrownBy(() -> importReplays(game("g1", 1, "Ash Ketchum", false), game("g2", 2, "Ash Ketchum", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El resultado de este partido ya se ha registrado.");
    }

    @Test
    void revert_clearsReplaysAndWarnings() {
        importReplays(game("g1", 1, "Ash Ketchum", false), game("g2", 2, "Ash Ketchum", true));

        new MatchResultService(leagueRepository, draftRepository, activityEventRepository).revoke(league, match, 1);

        assertThat(match.getReplayIds()).isNull();
        assertThat(match.getImportWarnings()).isNull();
    }

    @Test
    void commandType() {
        assertThat(handler.commandType()).isEqualTo(ImportSeriesCommand.class);
    }
}
