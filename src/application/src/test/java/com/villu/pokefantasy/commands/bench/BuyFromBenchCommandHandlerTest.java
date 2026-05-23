package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private UserRepository userRepository;
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
                draftRepository, closedListRepository, leagueRepository, userRepository,
                scheduleRepository, jornadaWindowService, activityEventRepository);

        lenient().when(scheduleRepository.findByLeagueId(LEAGUE_ID))
                .thenReturn(Optional.of(scheduleWithPendingJornada()));
        lenient().when(jornadaWindowService.isSwapWindowOpen(any())).thenReturn(true);
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
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("viernes a las 16:00");
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
    void handle_userNotMember_throwsIllegalArgument() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID))
                .thenReturn(Optional.of(leagueWithMembers(new LeagueMember("brock", LeagueRole.USER, 999))));

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalArgumentException.class)
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

        UserEntity ash   = userWithPokemons(0);
        UserEntity brock = userWithPokemon(POKEMON);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(benchEntry()));
        when(userRepository.findByUsername(USERNAME)).thenReturn(ash);
        when(userRepository.findByUsername("brock")).thenReturn(brock);

        assertThatThrownBy(() -> handler.handle(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available on the bench");
    }

    // ── Team size validation ──────────────────────────────────────────────────

    @Test
    void handle_teamFull_throwsIllegalState() {
        LeagueEntity league = leagueWithBuyer(999);
        league.setSettings(LeagueSettings.builder().maxTeamSize(2).build());

        UserEntity buyer = userWithPokemons(2); // already has 2 => at maxTeamSize

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(completedDraft()));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(benchEntry()));
        when(userRepository.findByUsername(USERNAME)).thenReturn(buyer);

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
        when(userRepository.findByUsername(USERNAME)).thenReturn(userWithPokemons(0));

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
        UserEntity buyer = userWithPokemons(0);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(userRepository.findByUsername(USERNAME)).thenReturn(buyer);

        handler.handle(cmd());

        // Coins deducted
        LeagueMember member = league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername())).findFirst().orElseThrow();
        assertThat(member.getCoinBalance()).isEqualTo(300); // 500 - 200
        verify(leagueRepository).save(league);

        // Pokemon added to user
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).updateUserWithPokemons(userCaptor.capture());
        assertThat(userCaptor.getValue().getPokemons())
                .anyMatch(p -> POKEMON.equals(p.getName()) && LEAGUE_ID.equals(p.getLeagueId()));

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
        UserEntity buyer = userWithPokemons(0);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(benchEntry()));
        when(userRepository.findByUsername(USERNAME)).thenReturn(buyer);

        handler.handle(cmd()); // must not throw

        // Coin save still happens (0 - 0 = 0 is still saved to keep code consistent)
        verify(leagueRepository).save(league);
        verify(userRepository).updateUserWithPokemons(any());
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
        when(userRepository.findByUsername(USERNAME)).thenReturn(userWithPokemons(0));

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

    /** UserEntity with N dummy pokemons in this league (for maxTeamSize tests). */
    private UserEntity userWithPokemons(int count) {
        UserEntity user = new UserEntity();
        user.setName(USERNAME);
        List<Pokemons> pokemons = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Pokemons p = new Pokemons();
            p.setName("pokemon-" + i);
            p.setLeagueId(LEAGUE_ID);
            pokemons.add(p);
        }
        user.setPokemons(pokemons);
        return user;
    }

    /** UserEntity with one named pokemon in this league (for owned-check tests). */
    private UserEntity userWithPokemon(String name) {
        UserEntity user = new UserEntity();
        user.setName("other-user");
        Pokemons p = new Pokemons();
        p.setName(name);
        p.setLeagueId(LEAGUE_ID);
        user.setPokemons(new ArrayList<>(List.of(p)));
        return user;
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
