package com.villu.pokefantasy.team;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
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
        assertThatThrownBy(() -> service.openMarket(LEAGUE, TeamOperation.SWAP)).hasMessageContaining("No schedule");

        when(scheduleRepository.findByLeagueId(LEAGUE)).thenReturn(Optional.of(new ScheduleEntity()));
        when(leagueRepository.findById(LEAGUE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.openMarket(LEAGUE, TeamOperation.SWAP))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("League not found");
    }

    // ── reglas ───────────────────────────────────────────────────────────────

    @Test
    void requireMember() {
        assertThat(service.requireMember(league, "ash").getCoinBalance()).isEqualTo(100);
        assertThatThrownBy(() -> service.requireMember(league, "gary")).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireUnlocked() {
        DraftPick pick = draft.getPicks().getFirst();
        assertThatCode(() -> service.requireUnlocked(pick)).doesNotThrowAnyException();

        pick.setLockedUntil(Instant.now().minusSeconds(1));
        assertThatCode(() -> service.requireUnlocked(pick)).doesNotThrowAnyException();

        pick.setLockedUntil(Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> service.requireUnlocked(pick))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'pikachu' está bloqueado");
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
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not available on the bench");
    }

    // ── movimientos ──────────────────────────────────────────────────────────

    @Test
    void transfer_changesOwnerLocksAndKeepsCustomPrice() {
        DraftPick rattata = draft.getPicks().get(1);
        Instant now = Instant.parse("2026-09-25T10:00:00Z");

        service.transfer(rattata, "misty", now);

        assertThat(rattata.getUsername()).isEqualTo("misty");
        assertThat(rattata.getPickedAt()).isEqualTo(now);
        assertThat(rattata.getLockedUntil()).isEqualTo(now.plus(TeamTransferService.TRANSFER_LOCK));
        assertThat(rattata.getCustomStealPrice()).isEqualTo(300);
    }

    @Test
    void benchMoves() {
        ClosedListEntity eevee = new ClosedListEntity();
        eevee.setPokemonName("eevee");
        eevee.setPokemonId(133);
        Instant now = Instant.now();

        DraftPick bought = service.addFromBench(draft, "ash", eevee, now);
        assertThat(bought.getRound()).isEqualTo(TeamTransferService.BENCH_PURCHASE_ROUND);
        assertThat(draft.getPicks()).contains(bought);

        DraftPick rattata = draft.getPicks().get(1);
        ClosedListEntity pidgey = new ClosedListEntity();
        pidgey.setPokemonName("pidgey");
        pidgey.setPokemonId(16);
        service.replaceWithBench(draft, rattata, pidgey);
        DraftPick replaced = draft.getPicks().get(1);
        assertThat(replaced.getPokemonName()).isEqualTo("pidgey");
        assertThat(replaced.getUsername()).isEqualTo("ash");
        assertThat(replaced.getRound()).isEqualTo(2);
        assertThat(replaced.getCustomStealPrice()).isNull();

        service.releaseToBench(draft, replaced);
        assertThat(draft.ownedPokemonNames()).containsExactlyInAnyOrder("pikachu", "eevee");
    }
}
