package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Jornada;
import com.villu.pokefantasy.repository.entity.ScheduleEntity.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrectMatchResultCommandHandlerTest {

    private static final String LEAGUE_ID = "l1";
    private static final String MATCH_ID = "m1";
    private static final String ADMIN = "ash";
    private static final String ASH = "ash";
    private static final String BROCK = "brock";

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private ActivityEventRepository activityEventRepository;

    private CorrectMatchResultCommandHandler handler;
    private LeagueEntity league;
    private Match match;

    @BeforeEach
    void setUp() {
        handler = new CorrectMatchResultCommandHandler(scheduleRepository, leagueRepository,
                new LeagueAdminGuard(leagueRepository),
                new MatchResultService(leagueRepository, draftRepository, activityEventRepository));

        // Ajustes actuales: 100/50. El partido se registró cuando daban 80/30 → ash 80, brock 30.
        league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        LeagueSettings settings = new LeagueSettings();
        settings.setCoinsPerWin(100);
        settings.setCoinsPerLoss(50);
        league.setSettings(settings);
        league.setMembers(new ArrayList<>(List.of(
                new LeagueMember(ASH, LeagueRole.ADMIN, 80),
                new LeagueMember(BROCK, LeagueRole.USER, 30),
                new LeagueMember("misty", LeagueRole.USER, 0))));
        lenient().when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        match = new Match(MATCH_ID, ASH, BROCK, ASH, MatchStatus.COMPLETED);
        match.setWinnerCoins(80);
        match.setLoserCoins(30);
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE_ID);
        schedule.setJornadas(new ArrayList<>(List.of(new Jornada(3, new ArrayList<>(List.of(match)), null))));
        lenient().when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));

        DraftEntity draft = new DraftEntity();
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick(ASH, "pikachu", 25, 1, Instant.now(), null, null),
                new DraftPick(BROCK, "onix", 95, 1, Instant.now(), null, null))));
        lenient().when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
    }

    private int coins(String username) {
        return league.getMembers().stream().filter(m -> m.getUsername().equals(username))
                .findFirst().orElseThrow().getCoinBalance();
    }

    private List<ActivityEventEntity> savedEvents() {
        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void revert_returnsTheCoinsActuallyAwardedAndLeavesMatchPending() {
        handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, null, ADMIN));

        // Devuelve 80/30 (lo que se dio), no 100/50 (los ajustes de ahora).
        assertThat(coins(ASH)).isZero();
        assertThat(coins(BROCK)).isZero();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.PENDING);
        assertThat(match.getWinnerUsername()).isNull();
        assertThat(match.getWinnerCoins()).isNull();
        verify(leagueRepository).save(league);
        verify(scheduleRepository).save(any());
        List<ActivityEventEntity> events = savedEvents();
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).satisfies(e -> {
            assertThat(e.getType()).isEqualTo(ActivityEventType.MATCH_RESULT_REVERTED);
            assertThat(e.getActorUsername()).isEqualTo(ASH);
            assertThat(e.getTargetUsername()).isEqualTo(BROCK);
            assertThat(e.getRoundNumber()).isEqualTo(3);
        });
        // Un COIN_REVOKED por jugador con lo retirado, para que su historial de monedas cuadre.
        assertThat(events.subList(1, 3)).allSatisfy(e -> {
            assertThat(e.getType()).isEqualTo(ActivityEventType.COIN_REVOKED);
            assertThat(e.getRoundNumber()).isEqualTo(3);
        });
        assertThat(events.subList(1, 3))
                .extracting(ActivityEventEntity::getActorUsername, ActivityEventEntity::getCoinsAmount)
                .containsExactly(tuple(ASH, 80), tuple(BROCK, 30));
    }

    @Test
    void correct_swapsWinnerWithCurrentSettings() {
        handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, BROCK, ADMIN));

        // ash: 80 - 80 + 50 (derrota) ; brock: 30 - 30 + 100 (victoria)
        assertThat(coins(ASH)).isEqualTo(50);
        assertThat(coins(BROCK)).isEqualTo(100);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(match.getWinnerUsername()).isEqualTo(BROCK);
        assertThat(match.getWinnerCoins()).isEqualTo(100);
        assertThat(match.getLoserCoins()).isEqualTo(50);
        assertThat(savedEvents()).extracting(ActivityEventEntity::getType).containsExactly(
                ActivityEventType.MATCH_RESULT_REVERTED,
                ActivityEventType.COIN_REVOKED, ActivityEventType.COIN_REVOKED,
                ActivityEventType.MATCH_RESULT,
                ActivityEventType.COIN_EARNED, ActivityEventType.COIN_EARNED);
    }

    @Test
    void revert_legacyResultWithoutRecordedCoins_usesCurrentSettings() {
        match.setWinnerCoins(null);
        match.setLoserCoins(null);

        handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, null, ADMIN));

        // Coins may go negative if they were already spent: the result must still be undone.
        assertThat(coins(ASH)).isEqualTo(-20);
        assertThat(coins(BROCK)).isEqualTo(-20);
    }

    @Test
    void revert_legacyResultAndNoSettings_returnsNothing() {
        match.setWinnerCoins(null);
        match.setLoserCoins(null);
        league.setSettings(null);

        handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, null, ADMIN));

        assertThat(coins(ASH)).isEqualTo(80);
        assertThat(coins(BROCK)).isEqualTo(30);
        // Nada retirado → ningún COIN_REVOKED.
        assertThat(savedEvents()).extracting(ActivityEventEntity::getType)
                .containsExactly(ActivityEventType.MATCH_RESULT_REVERTED);
    }

    @Test
    void correct_sameWinner_conflict() {
        assertThatThrownBy(() -> handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, ASH, ADMIN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya figura");
        verify(scheduleRepository, never()).save(any());
        assertThat(coins(ASH)).isEqualTo(80);
    }

    @Test
    void correct_winnerNotInMatch_badRequest() {
        assertThatThrownBy(() -> handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, "misty", ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void correct_newWinnerWithoutTeam_badRequest() {
        DraftEntity onlyAsh = new DraftEntity();
        onlyAsh.setPicks(new ArrayList<>(List.of(new DraftPick(ASH, "pikachu", 25, 1, Instant.now(), null, null))));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(onlyAsh));

        assertThatThrownBy(() -> handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, BROCK, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene Pokémon");
    }

    @Test
    void pendingMatch_nothingToCorrect() {
        match.setStatus(MatchStatus.PENDING);
        match.setWinnerUsername(null);

        assertThatThrownBy(() -> handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, null, ADMIN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aún no tiene resultado");
    }

    @Test
    void notAdmin_forbidden() {
        assertThatThrownBy(() -> handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, null, BROCK)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void unknownMatch_badRequest() {
        assertThatThrownBy(() -> handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, "nope", null, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noSchedule_conflict() {
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new CorrectMatchResultCommand(LEAGUE_ID, MATCH_ID, null, ADMIN)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(CorrectMatchResultCommand.class);
    }
}
