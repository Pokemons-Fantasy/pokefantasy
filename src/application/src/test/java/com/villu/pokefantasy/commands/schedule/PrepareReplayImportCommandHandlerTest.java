package com.villu.pokefantasy.commands.schedule;

import com.villu.pokefantasy.commands.schedule.replay.ReplayImportContext;
import com.villu.pokefantasy.dto.BattleGameType;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
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

import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrepareReplayImportCommandHandlerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeagueMembershipGuard leagueMembershipGuard;
    @Mock private LeagueRepository leagueRepository;
    @Mock private DraftRepository draftRepository;
    @Mock private ActivityEventRepository activityEventRepository;

    private Match match;
    private Jornada jornada;
    private LeagueEntity league;
    private PrepareReplayImportCommandHandler handler;

    private static UserEntity user(String name, String showdownId) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setShowdownId(showdownId);
        return u;
    }

    @BeforeEach
    void setUp() {
        match = new Match("m1", "ash", "misty", null, MatchStatus.PENDING);
        Match other = new Match("m2", "brock", "gary", "brock", MatchStatus.COMPLETED);
        other.setReplayIds(List.of("used-1", "used-2"));
        jornada = new Jornada(1, new ArrayList<>(List.of(match, other)), "2026-10-24"); // sábado
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId("l1");
        schedule.setJornadas(new ArrayList<>(List.of(jornada)));
        lenient().when(scheduleRepository.findByLeagueId("l1")).thenReturn(java.util.Optional.of(schedule));

        league = new LeagueEntity();
        league.setId("l1");
        league.setSettings(LeagueSettings.defaults());
        league.setMembers(List.of(new LeagueMember("ash", LeagueRole.USER, 0), new LeagueMember("misty", LeagueRole.USER, 0),
                new LeagueMember("boss", LeagueRole.ADMIN, 0), new LeagueMember("brock", LeagueRole.USER, 0)));
        lenient().when(leagueMembershipGuard.requireMember("l1", "ash")).thenReturn(league);
        lenient().when(leagueMembershipGuard.requireMember("l1", "boss")).thenReturn(league);
        lenient().when(leagueMembershipGuard.requireMember("l1", "brock")).thenReturn(league);
        lenient().when(userRepository.findByUsername("ash")).thenReturn(user("ash", "ashketchum"));
        lenient().when(userRepository.findByUsername("misty")).thenReturn(user("misty", "mistyw"));

        handler = new PrepareReplayImportCommandHandler(scheduleRepository, leagueMembershipGuard, new ReplayImportService(userRepository,
                new MatchResultService(leagueRepository, draftRepository, activityEventRepository)));
    }

    @Test
    void player_getsContextWithIdsFormatWeekAndUsedReplays() {
        ReplayImportContext ctx = handler.handle(new PrepareReplayImportCommand("l1", "m1", "ash"));

        assertThat(ctx.player1()).isEqualTo("ash");
        assertThat(ctx.player2Id()).isEqualTo("mistyw");
        assertThat(ctx.baseFormat()).isEqualTo("gen9doublescustomgame");
        assertThat(ctx.fromEpoch()).isEqualTo(LocalDate.of(2026, 10, 19)
                .atStartOfDay(ZoneId.of("Europe/Madrid")).toEpochSecond());
        assertThat(ctx.usedReplayIds()).containsExactlyInAnyOrder("used-1", "used-2");
        assertThat(ctx.bestOf()).isEqualTo(3);
    }

    @Test
    void admin_canImport_singlesFormat() {
        league.getSettings().setBattleGameType(BattleGameType.SINGLES);

        assertThat(handler.handle(new PrepareReplayImportCommand("l1", "m1", "boss")).baseFormat())
                .isEqualTo("gen9customgame");
    }

    @Test
    void otherMember_forbidden() {
        assertThatThrownBy(() -> handler.handle(new PrepareReplayImportCommand("l1", "m1", "brock")))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Solo los jugadores del partido o un admin pueden importar el resultado.");
    }

    @Test
    void completedMatch_conflict() {
        match.setStatus(MatchStatus.COMPLETED);

        assertThatThrownBy(() -> handler.handle(new PrepareReplayImportCommand("l1", "m1", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El resultado de este partido ya se ha registrado.");
    }

    @Test
    void playerWithoutShowdown_badRequest() {
        when(userRepository.findByUsername("misty")).thenReturn(user("misty", null));

        assertThatThrownBy(() -> handler.handle(new PrepareReplayImportCommand("l1", "m1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("misty no ha vinculado su cuenta de Showdown.");
    }

    @Test
    void jornadaWithoutDate_noWeekFilter_andLeagueWithoutSettings_defaults() {
        jornada.setStartDate(null);
        league.setSettings(null);

        ReplayImportContext ctx = handler.handle(new PrepareReplayImportCommand("l1", "m1", "ash"));

        assertThat(ctx.fromEpoch()).isNull();
        assertThat(ctx.baseFormat()).isEqualTo("gen9doublescustomgame");
        assertThat(ctx.bestOf()).isEqualTo(3);
    }

    @Test
    void commandType() {
        assertThat(handler.commandType()).isEqualTo(PrepareReplayImportCommand.class);
    }
}
