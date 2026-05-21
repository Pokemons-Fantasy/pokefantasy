package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.MatchStatus;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
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
class SwapWithBenchCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private JornadaWindowService jornadaWindowService;

    private SwapWithBenchCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String USERNAME = "ash";
    private static final String GIVE = "charizard";
    private static final String TAKE = "pikachu";

    @BeforeEach
    void setUp() {
        handler = new SwapWithBenchCommandHandler(
                draftRepository, closedListRepository, leagueRepository, userRepository,
                scheduleRepository, jornadaWindowService);
        // Default: no schedule → no time restriction
        lenient().when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());
    }

    @Test
    void handle_draftNotCompleted_throwsIllegalState() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only allowed after the draft is completed");
    }

    @Test
    void handle_draftInProgress_throwsIllegalState() {
        DraftEntity draft = draftWithStatus(DraftStatus.IN_PROGRESS);
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handle_leagueNotFound_throwsIllegalArgument() {
        DraftEntity draft = draftWithStatus(DraftStatus.COMPLETED);
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("League not found");
    }

    @Test
    void handle_userNotMember_throwsIllegalState() {
        DraftEntity draft = draftWithStatus(DraftStatus.COMPLETED);
        LeagueEntity league = leagueWithMembers(new LeagueMember("brock", LeagueRole.USER, 0));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void handle_userEntityNotFound_throwsIllegalArgument() {
        DraftEntity draft = draftWithStatus(DraftStatus.COMPLETED);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void handle_pokemonNotInTeam_throwsIllegalArgument() {
        DraftEntity draft = draftWithStatus(DraftStatus.COMPLETED);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        UserEntity user = userWithPokemons(new Pokemons());
        user.getPokemons().get(0).setName("squirtle");
        user.getPokemons().get(0).setLeagueId(LEAGUE_ID);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in your team");
    }

    @Test
    void handle_pokemonNotInPool_throwsIllegalArgument() {
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        UserEntity user = userWithPokemon(GIVE);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the pool");
    }

    @Test
    void handle_pokemonAlreadyOwned_throwsIllegalState() {
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueMember ashMember = new LeagueMember(USERNAME, LeagueRole.USER, 0);
        LeagueMember brockMember = new LeagueMember("brock", LeagueRole.USER, 0);
        LeagueEntity league = leagueWithMembers(ashMember, brockMember);

        UserEntity ash = userWithPokemon(GIVE);
        UserEntity brock = userWithPokemon(TAKE);
        brock.getPokemons().get(0).setLeagueId(LEAGUE_ID);

        ClosedListEntity entry = closedListEntry(TAKE, 25);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(ash);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(userRepository.findByUsername("brock")).thenReturn(brock);

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available on the bench");
    }

    @Test
    void handle_happyPath_swapsUserPokemonAndUpdatesDraftPick() {
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity entry = closedListEntry(TAKE, 25);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        // User now has pikachu instead of charizard
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).updateUserWithPokemons(userCaptor.capture());
        List<Pokemons> pokemons = userCaptor.getValue().getPokemons();
        assertThat(pokemons).anyMatch(p -> TAKE.equals(p.getName()));
        assertThat(pokemons).noneMatch(p -> GIVE.equals(p.getName()));

        // Draft pick updated from charizard → pikachu
        ArgumentCaptor<DraftEntity> draftCaptor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getPicks())
                .anyMatch(p -> USERNAME.equals(p.getUsername()) && TAKE.equals(p.getPokemonName()));
    }

    @Test
    void handle_swapWindowClosed_throwsIllegalState() {
        DraftEntity draft = draftWithStatus(DraftStatus.COMPLETED);
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        // Build a schedule with a closed swap window
        ScheduleEntity schedule = scheduleWithPendingJornada();
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isSwapWindowOpen(schedule)).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("viernes a las 16:00");
    }

    @Test
    void handle_noSchedule_swapAllowed() {
        // No schedule → no restriction → fall through to normal business validations
        DraftEntity draft = draftWithStatus(DraftStatus.COMPLETED);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity entry = closedListEntry(TAKE, 25);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        verify(userRepository).updateUserWithPokemons(any());
        verify(draftRepository).save(any());
    }

    // ── Tier parity + net coin change ─────────────────────────────────────────

    @Test
    void handle_giveLowerTierForHigher_throwsIllegalState() {
        // D → S is blocked: can't trade up tier
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 9999));
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.D);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("igual o mejor tier");
    }

    @Test
    void handle_giveSameTier_noCoinChange() {
        // A → A: same tier, no net coin change
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 100));
        league.setSettings(settingsWithTierAPrice(200));
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.A);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        LeagueMember member = league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername())).findFirst().orElseThrow();
        assertThat(member.getCoinBalance()).isEqualTo(100); // unchanged
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void handle_giveHigherTier_receivesRefund() {
        // S → D: player gives S (priceTierS=500), takes D (priceTierD=0) → receives 500 coins
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 100));
        league.setSettings(LeagueSettings.builder().priceTierS(500).priceTierD(0).build());
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.S);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.D);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        LeagueMember member = league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername())).findFirst().orElseThrow();
        assertThat(member.getCoinBalance()).isEqualTo(600); // 100 + 500 refund
        verify(leagueRepository).save(league);
        verify(userRepository).updateUserWithPokemons(any());
    }

    @Test
    void handle_insufficientCoins_throwsIllegalState() {
        // S(priceTierS=0) → A(priceTierA=200): net = 0-200 = -200, balance 50 → throws
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 50));
        league.setSettings(settingsWithTierAPrice(200)); // priceTierS=0, priceTierA=200
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.S); // S is better tier than A → allowed by parity
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        assertThatThrownBy(() -> handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tienes suficientes monedas")
                .hasMessageContaining("200")
                .hasMessageContaining("50");
    }

    @Test
    void handle_sufficientCoins_netNegative_deductsFromBalance() {
        // S(priceTierS=0) → A(priceTierA=200): net = -200, balance 300 → 100
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 300));
        league.setSettings(settingsWithTierAPrice(200));
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.S);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        LeagueMember member = league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername())).findFirst().orElseThrow();
        assertThat(member.getCoinBalance()).isEqualTo(100);
        verify(leagueRepository).save(league);
        verify(userRepository).updateUserWithPokemons(any());
    }

    @Test
    void handle_tierB_giveA_deductsNet() {
        // A(priceTierA=0) → B(priceTierB=150): net = 0-150 = -150, balance 300 → 150
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 300));
        league.setSettings(LeagueSettings.builder().priceTierB(150).build());
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.A);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.B);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        LeagueMember member = league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername())).findFirst().orElseThrow();
        assertThat(member.getCoinBalance()).isEqualTo(150);
    }

    @Test
    void handle_tierC_giveA_deductsNet() {
        // A(priceTierA=0) → C(priceTierC=75): net = 0-75 = -75, balance 200 → 125
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 200));
        league.setSettings(LeagueSettings.builder().priceTierC(75).build());
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.A);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.C);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        LeagueMember member = league.getMembers().stream()
                .filter(m -> USERNAME.equals(m.getUsername())).findFirst().orElseThrow();
        assertThat(member.getCoinBalance()).isEqualTo(125);
    }

    @Test
    void handle_nullSettings_sameTier_swapFree() {
        // No settings → all prices = 0, A→A, net=0
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.A);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        verify(leagueRepository, never()).save(any());
        verify(userRepository).updateUserWithPokemons(any());
    }

    @Test
    void handle_nullPriceTierField_treatedAsZero() {
        // settings present but priceTierA not set → null → 0, A→A, net=0
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        league.setSettings(LeagueSettings.builder().build());
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity giveEntry = closedListEntry(GIVE, 6);
        giveEntry.setTier(Tier.A);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(GIVE, LEAGUE_ID))
                .thenReturn(Optional.of(giveEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        verify(leagueRepository, never()).save(any());
        verify(userRepository).updateUserWithPokemons(any());
    }

    @Test
    void handle_zeroTierPrice_swapFreeRegardlessOfBalance() {
        // D → D, priceTierD=0, net=0 → free regardless of balance
        DraftEntity draft = completedDraftWithPick(USERNAME, GIVE, 6);
        LeagueEntity league = leagueWithMembers(new LeagueMember(USERNAME, LeagueRole.USER, 0));
        LeagueSettings settings = LeagueSettings.builder().priceTierD(0).build();
        league.setSettings(settings);
        UserEntity user = userWithPokemon(GIVE);
        ClosedListEntity takeEntry = closedListEntry(TAKE, 25);
        takeEntry.setTier(Tier.D);
        // GIVE has no stub → Optional.empty() → null tier → rank D = same as take tier D

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TAKE, LEAGUE_ID))
                .thenReturn(Optional.of(takeEntry));

        handler.handle(new SwapWithBenchCommand(LEAGUE_ID, USERNAME, GIVE, TAKE));

        verify(leagueRepository, never()).save(any());
        verify(userRepository).updateUserWithPokemons(any());
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(SwapWithBenchCommand.class);
    }

    // --- helpers ---

    private DraftEntity draftWithStatus(DraftStatus status) {
        DraftEntity draft = new DraftEntity();
        draft.setId("draft-1");
        draft.setStatus(status);
        draft.setLeagueId(LEAGUE_ID);
        draft.setPicks(new ArrayList<>());
        draft.setTurnOrder(List.of(USERNAME));
        return draft;
    }

    private DraftEntity completedDraftWithPick(String username, String pokemon, int pokemonId) {
        DraftEntity draft = draftWithStatus(DraftStatus.COMPLETED);
        draft.getPicks().add(new DraftPick(username, pokemon, pokemonId, 1, Instant.now(), null, null));
        return draft;
    }

    private LeagueEntity leagueWithMembers(LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setMembers(new ArrayList<>(List.of(members)));
        return league;
    }

    private UserEntity userWithPokemons(Pokemons... pokemons) {
        UserEntity user = new UserEntity();
        user.setName(USERNAME);
        user.setPokemons(new ArrayList<>(List.of(pokemons)));
        return user;
    }

    private UserEntity userWithPokemon(String name) {
        Pokemons p = new Pokemons();
        p.setName(name);
        p.setLeagueId(LEAGUE_ID);
        return userWithPokemons(p);
    }

    private ClosedListEntity closedListEntry(String name, int id) {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonName(name);
        entry.setPokemonId(id);
        entry.setLeagueId(LEAGUE_ID);
        return entry;
    }

    private LeagueSettings settingsWithTierAPrice(int price) {
        return LeagueSettings.builder()
                .priceTierS(0).priceTierA(price).priceTierB(0).priceTierC(0).priceTierD(0)
                .build();
    }

    private ScheduleEntity scheduleWithPendingJornada() {
        Match match = new Match("mid1", "ash", "brock", null, MatchStatus.PENDING);
        Jornada jornada = new Jornada(1, new ArrayList<>(List.of(match)), "2026-06-06");
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE_ID);
        schedule.setJornadas(new ArrayList<>(List.of(jornada)));
        return schedule;
    }
}
