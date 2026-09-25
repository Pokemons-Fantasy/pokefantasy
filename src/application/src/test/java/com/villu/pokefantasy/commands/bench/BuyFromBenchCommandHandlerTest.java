package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueMemberService;
import com.villu.pokefantasy.league.TierPricingService;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
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
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuyFromBenchCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private JornadaWindowService jornadaWindowService;
    @Mock private ActivityEventRepository activityEventRepository;

    private BuyFromBenchCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String USERNAME  = "ash";
    private static final String POKEMON   = "dragonite";
    private static final int    POKEMON_ID = 149;

    @BeforeEach
    void setUp() {
        handler = new BuyFromBenchCommandHandler(
                draftRepository, closedListRepository, leagueRepository,
                scheduleRepository, jornadaWindowService, activityEventRepository,
                new LeagueMemberService(), new TierPricingService());

        lenient().when(scheduleRepository.findByLeagueId(LEAGUE_ID))
                .thenReturn(Optional.of(scheduleWithPendingJornada()));
        lenient().when(jornadaWindowService.isSwapWindowOpen(any(), any())).thenReturn(true);
        lenient().when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWithBuyer(500)));
    }

    // ── Draft validation ──────────────────────────────────────────────────────

    @Test
    void handle_draftNotCompleted_throwsIllegalState() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only allowed after the draft is completed");
    }

    @Test
    void handle_draftInProgress_throwsIllegalState() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID))
                .thenReturn(Optional.of(draftWithStatus(DraftStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Window validation ─────────────────────────────────────────────────────

    @Test
    void handle_swapWindowClosed_throwsIllegalState() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID))
                .thenReturn(Optional.of(draftWithStatus(DraftStatus.COMPLETED)));
        ScheduleEntity schedule = scheduleWithPendingJornada();
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("El plazo cerró");
    }

    @Test
    void handle_noSchedule_throwsIllegalState() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID))
                .thenReturn(Optional.of(draftWithStatus(DraftStatus.COMPLETED)));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No schedule found");
    }

    // ── Member/user validation ────────────────────────────────────────────────

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID))
                .thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_userNotMember_throwsForbiddenOperation() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID))
                .thenReturn(Optional.of(leagueWithMembers(new LeagueMember("brock", LeagueRole.USER, 999))));

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not a member");
    }

    // ── Pokemon validation ────────────────────────────────────────────────────

    @Test
    void handle_pokemonNotInPool_throwsIllegalArgument() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(leagueWithBuyer(100)));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the pool");
    }

    @Test
    void handle_pokemonAlreadyOwned_throwsIllegalState() {
        LeagueMember ashMember   = new LeagueMember(USERNAME, LeagueRole.USER, 100);
        LeagueMember brockMember = new LeagueMember("brock", LeagueRole.USER, 0);
        LeagueEntity league = leagueWithMembers(ashMember, brockMember);

        // El nombre del pick se compara sin distinguir mayúsculas.
        DraftEntity draft = completedDraftWith(pick("brock", POKEMON.toUpperCase()));

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(benchEntry()));

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available on the bench");
    }

    // ── Team size validation ──────────────────────────────────────────────────

    @Test
    void handle_teamFull_throwsIllegalState() {
        LeagueEntity league = leagueWithBuyer(999);
        league.setSettings(LeagueSettings.builder().maxTeamSize(2).build());

        // El comprador ya tiene 2 picks => maxTeamSize alcanzado
        DraftEntity draft = completedDraftWith(pick(USERNAME, "pokemon-0"), pick(USERNAME, "pokemon-1"));

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(benchEntry()));

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lleno");
    }

    // ── Coin validation ───────────────────────────────────────────────────────

    @Test
    void handle_insufficientCoins_throwsIllegalState() {
        LeagueEntity league = leagueWithBuyer(10);
        league.setSettings(LeagueSettings.builder().priceTierA(200).build());

        ClosedListEntity entry = benchEntry();
        entry.setTier(Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tienes suficientes monedas")
                .hasMessageContaining("200")
                .hasMessageContaining("10");
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_happyPath_deductsCoinsAndAddsToTeamAndDraft() {
        LeagueEntity league = leagueWithBuyer(500);
        league.setSettings(LeagueSettings.builder().priceTierA(200).build());

        ClosedListEntity entry = benchEntry();
        entry.setTier(Tier.A);

        DraftEntity draft = completedDraft();

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(cmd());

        // Coins deducted
        LeagueMember member = league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername())).findFirst().orElseThrow();
        assertThat(member.getCoinBalance()).isEqualTo(300); // 500 - 200
        verify(leagueRepository).save(league);

        // DraftPick added with round=0
        ArgumentCaptor<DraftEntity> draftCaptor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getPicks())
                .anyMatch(p -> USERNAME.equals(p.getUsername())
                        && POKEMON.equals(p.getPokemonName())
                        && p.getRound() == 0);
    }

    @Test
    void handle_happyPath_zeroPriceWhenNoSettings() {
        // No settings → price = 0, any coin balance is sufficient
        LeagueEntity league = leagueWithBuyer(0);

        DraftEntity draft = completedDraft();

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(benchEntry()));

        handler.handle(cmd()); // must not throw

        // Coin save still happens (0 - 0 = 0 is still saved to keep code consistent)
        verify(leagueRepository).save(league);
        verify(draftRepository).save(any());
    }

    @Test
    void handle_happyPath_savesActivityEvent() {
        LeagueEntity league = leagueWithBuyer(500);
        league.setSettings(LeagueSettings.builder().priceTierS(300).build());

        ClosedListEntity entry = benchEntry();
        entry.setTier(Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(cmd());

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository).save(captor.capture());
        ActivityEventEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(ActivityEventType.BENCH_PURCHASE);
        assertThat(saved.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(saved.getActorUsername()).isEqualTo(USERNAME);
        assertThat(saved.getPokemonName()).isEqualTo(POKEMON);
        assertThat(saved.getCoinsAmount()).isEqualTo(300);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ── Concurrencia: draft.save falla → se propaga sin compensar ──────────

    @Test
    void handle_draftSaveConflict_propagatesWithoutCompensating() {
        LeagueEntity league = leagueWithBuyer(500);
        league.setSettings(LeagueSettings.builder().priceTierA(200).build());
        ClosedListEntity entry = benchEntry();
        entry.setTier(Tier.A);
        DraftEntity draft = completedDraft();

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        doThrow(new OptimisticLockingFailureException("stale draft")).when(draftRepository).save(draft);

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Sin compensaciones manuales: el conflicto se propaga intacto para que la transacción
        // del mediator deshaga todas las escrituras y reintente el comando.
        verify(leagueRepository, times(1)).save(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(BuyFromBenchCommand.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BuyFromBenchCommand cmd() {
        return new BuyFromBenchCommand(LEAGUE_ID, USERNAME, POKEMON);
    }

    private DraftEntity draftWithStatus(DraftStatus status) {
        DraftEntity draft = new DraftEntity();
        draft.setId("draft-1");
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(status);
        draft.setPicks(new ArrayList<>());
        draft.setTurnOrder(List.of(USERNAME));
        return draft;
    }

    private DraftEntity completedDraft() {
        return draftWithStatus(DraftStatus.COMPLETED);
    }

    private LeagueEntity leagueWithMembers(LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(new ArrayList<>(List.of(members)));
        return league;
    }

    /** League with a single member (the buyer) at the given coin balance. */
    private LeagueEntity leagueWithBuyer(int coins) {
        return leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, coins));
    }

    private DraftEntity completedDraftWith(DraftPick... picks) {
        DraftEntity draft = completedDraft();
        draft.getPicks().addAll(List.of(picks));
        return draft;
    }

    private DraftPick pick(String username, String pokemonName) {
        return new DraftPick(username, pokemonName, 0, 1, Instant.now(), null, null);
    }

    private ClosedListEntity benchEntry() {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonName(POKEMON);
        entry.setPokemonId(POKEMON_ID);
        entry.setLeagueId(LEAGUE_ID);
        return entry;
    }

    private ScheduleEntity scheduleWithPendingJornada() {
        Match match = new Match("m1", USERNAME, "brock", null, MatchStatus.PENDING);
        Jornada jornada = new Jornada(1, new ArrayList<>(List.of(match)), "2026-06-06");
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE_ID);
        schedule.setJornadas(new ArrayList<>(List.of(jornada)));
        return schedule;
    }
}
