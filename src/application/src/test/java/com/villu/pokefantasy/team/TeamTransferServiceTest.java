package com.villu.pokefantasy.team;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamTransferServiceTest {

    private static final String LEAGUE = "l1";

    @Mock private DraftRepository draftRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private JornadaWindowService jornadaWindowService;

    private TeamTransferService service;
    private DraftEntity draft;
    private LeagueEntity league;

    @BeforeEach
    void setUp() {
        service = new TeamTransferService(draftRepository, scheduleRepository, leagueRepository, jornadaWindowService);
        draft = new DraftEntity();
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(
                new DraftPick("ash", "pikachu", 25, 1, Instant.EPOCH, null, null),
                new DraftPick("ash", "rattata", 19, 2, Instant.EPOCH, 300, null))));
        league = new LeagueEntity();
        league.setId(LEAGUE);
        league.setMembers(new ArrayList<>(List.of(new LeagueMember("ash", LeagueRole.USER, 100))));
    }

    private void marketData() {
        when(draftRepository.findLatestByLeagueId(LEAGUE)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE)).thenReturn(Optional.of(new ScheduleEntity()));
        when(leagueRepository.findById(LEAGUE)).thenReturn(Optional.of(league));
    }

    // ── openMarket ───────────────────────────────────────────────────────────

    @Test
    void openMarket_stealUsesStealWindow() {
        marketData();
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);

        TeamTransferService.Market market = service.openMarket(LEAGUE, TeamOperation.STEAL);

        assertThat(market.draft()).isSameAs(draft);
        assertThat(market.league()).isSameAs(league);
    }

    @ParameterizedTest
    @EnumSource(value = TeamOperation.class, names = {"TRADE", "SWAP", "BUY", "RELEASE"})
    void openMarket_othersUseSwapWindow_andReportClosedWindowWithTheirMessage(TeamOperation operation) {
        marketData();
        when(jornadaWindowService.isSwapWindowOpen(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.openMarket(LEAGUE, operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(operation.windowClosedMessage);
    }

    @ParameterizedTest
    @EnumSource(TeamOperation.class)
    void openMarket_draftNotCompleted(TeamOperation operation) {
        draft.setStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId(LEAGUE)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.openMarket(LEAGUE, operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(operation.draftNotCompletedMessage);
    }

    @Test
    void openMarket_missingScheduleOrLeague() {
        when(draftRepository.findLatestByLeagueId(LEAGUE)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.openMarket(LEAGUE, TeamOperation.SWAP)).hasMessageContaining("aún no tiene calendario");

        when(scheduleRepository.findByLeagueId(LEAGUE)).thenReturn(Optional.of(new ScheduleEntity()));
        when(leagueRepository.findById(LEAGUE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.openMarket(LEAGUE, TeamOperation.SWAP))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Liga no encontrada");
    }

    // ── reglas ───────────────────────────────────────────────────────────────

    @Test
    void requireMember() {
        assertThat(service.requireMember(league, "ash").getCoinBalance()).isEqualTo(100);
        assertThatThrownBy(() -> service.requireMember(league, "gary")).isInstanceOf(ForbiddenOperationException.class);
    }

    // ── bloqueo hasta fin de jornada (regla real del calendario) ────────────

    /** Servicio con el {@link JornadaWindowService} real: el bloqueo se deriva del calendario. */
    private TeamTransferService withRealWindows() {
        return new TeamTransferService(draftRepository, scheduleRepository, leagueRepository, new JornadaWindowService());
    }

    private static ScheduleEntity.Jornada jornada(int round, MatchStatus... statuses) {
        List<ScheduleEntity.Match> matches = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            matches.add(new ScheduleEntity.Match("m" + round + i, "ash", "brock",
                    statuses[i] == MatchStatus.COMPLETED ? "ash" : null, statuses[i]));
        }
        return new ScheduleEntity.Jornada(round, matches, null);
    }

    private static ScheduleEntity schedule(ScheduleEntity.Jornada... jornadas) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setJornadas(new ArrayList<>(List.of(jornadas)));
        return schedule;
    }

    private DraftPick lockedThrough(Integer jornada) {
        DraftPick pick = draft.getPicks().getFirst();
        pick.setLockedThroughJornada(jornada);
        return pick;
    }

    @Test
    void isLocked_jornadaPendiente_true() {
        ScheduleEntity schedule = schedule(jornada(1, MatchStatus.COMPLETED), jornada(2, MatchStatus.COMPLETED, MatchStatus.PENDING));
        assertThat(withRealWindows().isLocked(lockedThrough(2), schedule)).isTrue();
    }

    @Test
    void isLocked_jornadaCompletada_false() {
        ScheduleEntity schedule = schedule(jornada(1, MatchStatus.COMPLETED), jornada(2, MatchStatus.COMPLETED, MatchStatus.COMPLETED));
        assertThat(withRealWindows().isLocked(lockedThrough(2), schedule)).isFalse();
    }

    @Test
    void isLocked_resultadoDeshecho_vuelveABloquear() {
        ScheduleEntity schedule = schedule(jornada(1, MatchStatus.COMPLETED), jornada(2, MatchStatus.COMPLETED));
        DraftPick pick = lockedThrough(2);
        TeamTransferService real = withRealWindows();
        assertThat(real.isLocked(pick, schedule)).isFalse();

        schedule.getJornadas().get(1).getMatches().getFirst().setStatus(MatchStatus.PENDING);
        assertThat(real.isLocked(pick, schedule)).isTrue();
    }

    @Test
    void isLocked_sinJornada_false() {
        ScheduleEntity schedule = schedule(jornada(1, MatchStatus.PENDING));
        assertThat(withRealWindows().isLocked(lockedThrough(null), schedule)).isFalse();
    }

    @Test
    void isLocked_jornadaInexistente_false() {
        ScheduleEntity schedule = schedule(jornada(1, MatchStatus.PENDING), jornada(2, MatchStatus.PENDING));
        assertThat(withRealWindows().isLocked(lockedThrough(9), schedule)).isFalse();
    }

    @Test
    void requireUnlocked_bloqueado_mensaje() {
        ScheduleEntity schedule = schedule(jornada(1, MatchStatus.COMPLETED), jornada(2, MatchStatus.PENDING));
        DraftPick pick = lockedThrough(2);
        pick.setPokemonName("Lapras");

        assertThatThrownBy(() -> withRealWindows().requireUnlocked(pick, schedule))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("'Lapras' está bloqueado hasta que termine la jornada 2.");
        assertThatCode(() -> withRealWindows().requireUnlocked(lockedThrough(1), schedule)).doesNotThrowAnyException();
    }

    @Test
    void currentJornada() {
        TeamTransferService real = withRealWindows();
        assertThat(real.currentJornada(schedule(jornada(1, MatchStatus.COMPLETED), jornada(2, MatchStatus.PENDING)))).isEqualTo(2);
        assertThatThrownBy(() -> real.currentJornada(schedule(jornada(1, MatchStatus.COMPLETED))))
                .isInstanceOf(IllegalStateException.class).hasMessage("La temporada ha terminado");
    }

    @Test
    void charge() {
        LeagueMember ash = league.getMembers().getFirst();
        service.charge(ash, 60);
        assertThat(ash.getCoinBalance()).isEqualTo(40);

        assertThatThrownBy(() -> service.charge(ash, 41))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Necesitas 41 pero tienes 40");
        assertThat(ash.getCoinBalance()).isEqualTo(40);
    }

    @Test
    void requireOnBench() {
        assertThatCode(() -> service.requireOnBench(draft, "eevee")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireOnBench(draft, "Pikachu"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no está en el banquillo");
    }

    // ── movimientos ──────────────────────────────────────────────────────────

    @Test
    void transfer_changesOwnerLocksAndKeepsCustomPrice() {
        DraftPick rattata = draft.getPicks().get(1);
        Instant now = Instant.parse("2026-09-25T10:00:00Z");

        rattata.setLockedUntil(now);

        service.transfer(rattata, "misty", 3, now);

        assertThat(rattata.getUsername()).isEqualTo("misty");
        assertThat(rattata.getPickedAt()).isEqualTo(now);
        assertThat(rattata.getLockedThroughJornada()).isEqualTo(3);
        assertThat(rattata.getLockedUntil()).isNull();
        assertThat(rattata.getCustomStealPrice()).isEqualTo(300);
    }

    @Test
    void benchMoves() {
        ClosedListEntity eevee = new ClosedListEntity();
        eevee.setPokemonName("eevee");
        eevee.setPokemonId(133);
        Instant now = Instant.now();

        DraftPick bought = service.addFromBench(draft, "ash", eevee, 3, now);
        assertThat(bought.getRound()).isEqualTo(TeamTransferService.BENCH_PURCHASE_ROUND);
        assertThat(draft.getPicks()).contains(bought);
        assertThat(bought.getLockedThroughJornada()).isEqualTo(3);
        assertThat(bought.getLockedUntil()).isNull();

        DraftPick rattata = draft.getPicks().get(1);
        ClosedListEntity pidgey = new ClosedListEntity();
        pidgey.setPokemonName("pidgey");
        pidgey.setPokemonId(16);
        service.replaceWithBench(draft, rattata, pidgey, 3);
        DraftPick replaced = draft.getPicks().get(1);
        assertThat(replaced.getPokemonName()).isEqualTo("pidgey");
        assertThat(replaced.getUsername()).isEqualTo("ash");
        assertThat(replaced.getRound()).isEqualTo(2);
        assertThat(replaced.getCustomStealPrice()).isNull();
        assertThat(replaced.getLockedThroughJornada()).isEqualTo(3);
        assertThat(replaced.getLockedUntil()).isNull();

        service.releaseToBench(draft, replaced);
        assertThat(draft.ownedPokemonNames()).containsExactlyInAnyOrder("pikachu", "eevee");
    }
}
