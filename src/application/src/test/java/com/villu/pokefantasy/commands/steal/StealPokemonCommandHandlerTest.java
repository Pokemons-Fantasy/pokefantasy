package com.villu.pokefantasy.commands.steal;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.PushNotificationPort;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StealPokemonCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private JornadaWindowService jornadaWindowService;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private PushNotificationPort pushNotificationPort;

    private StealPokemonCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String STEALER  = "ash";
    private static final String VICTIM   = "brock";
    private static final String TARGET   = "charizard";

    @BeforeEach
    void setUp() {
        handler = new StealPokemonCommandHandler(
                draftRepository, closedListRepository, leagueRepository,
                userRepository, scheduleRepository, jornadaWindowService,
                activityEventRepository, pushNotificationPort);

        // Default stub — tests that need specific settings override this
        LeagueEntity defaultLeague = leagueWithTwoMembers(1000, 500);
        defaultLeague.setSettings(LeagueSettings.builder().priceTierS(300).priceTierA(200).priceTierB(150).priceTierC(100).priceTierD(50).build());
        lenient().when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(defaultLeague));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_happyPath_transfersPokemonAndCoins() {
        int stealPrice = 300;

        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500); // stealer:1000, victim:500
        league.setSettings(LeagueSettings.builder().priceTierS(stealPrice).build());

        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        UserEntity stealerUser = userWithPokemon(STEALER, "pikachu");
        UserEntity victimUser  = userWithPokemon(VICTIM, TARGET);
        when(userRepository.findByUsername(STEALER)).thenReturn(stealerUser);
        when(userRepository.findByUsername(VICTIM)).thenReturn(victimUser);

        String result = handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET));

        // Coin transfer: stealer -300, victim +600
        LeagueMember stealerMember = getMember(league, STEALER);
        LeagueMember victimMember  = getMember(league, VICTIM);
        assertThat(stealerMember.getCoinBalance()).isEqualTo(700);  // 1000-300
        assertThat(victimMember.getCoinBalance()).isEqualTo(1100);  // 500+600

        // Pokemon moved: stealer now has TARGET, victim lost it
        assertThat(stealerUser.getPokemons()).anyMatch(p -> TARGET.equals(p.getName()));
        assertThat(victimUser.getPokemons()).noneMatch(p -> TARGET.equals(p.getName()));

        // Draft pick ownership transferred
        ArgumentCaptor<DraftEntity> draftCaptor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        DraftPick pick = draftCaptor.getValue().getPicks().stream()
                .filter(p -> TARGET.equalsIgnoreCase(p.getPokemonName()))
                .findFirst().orElseThrow();
        assertThat(pick.getUsername()).isEqualTo(STEALER);
        assertThat(pick.getLockedUntil()).isNotNull();
        assertThat(pick.getLockedUntil()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));

        verify(leagueRepository).save(league);
        verify(userRepository, times(2)).updateUserWithPokemons(any());

        assertThat(result).isEqualTo(VICTIM);
    }

    // ── Steal window closed ───────────────────────────────────────────────────

    @Test
    void handle_stealWindowClosed_throws() {
        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ventana de robos");
    }

    // ── Steal own pokemon ─────────────────────────────────────────────────────

    @Test
    void handle_stealOwnPokemon_throws() {
        // TARGET owned by STEALER — not in anyone else's team
        DraftEntity draft = completedDraftWithPick(STEALER, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equipo de ningún rival");
    }

    // ── Pokemon locked ────────────────────────────────────────────────────────

    @Test
    void handle_pokemonLocked_throws() {
        // Pick locked until 1 hour from now
        DraftPick pick = new DraftPick(VICTIM, TARGET, 6, 1, Instant.now(), null,
                Instant.now().plus(1, ChronoUnit.HOURS));
        DraftEntity draft = draftWithPick(pick);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bloqueado");
    }

    // ── Insufficient coins ────────────────────────────────────────────────────

    @Test
    void handle_insufficientCoins_throws() {
        int stealPrice = 500;

        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(100, 0); // stealer only has 100
        league.setSettings(LeagueSettings.builder().priceTierS(stealPrice).build());

        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficientes monedas")
                .hasMessageContaining("500")
                .hasMessageContaining("100");
    }

    // ── Custom steal price inherited ──────────────────────────────────────────

    @Test
    void handle_customPriceInherited() {
        // Pick has customStealPrice=800 (owner raised it); default tier price would be 300
        DraftPick pick = new DraftPick(VICTIM, TARGET, 6, 1, Instant.now(), 800, null);
        DraftEntity draft = draftWithPick(pick);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 0);
        league.setSettings(LeagueSettings.builder().priceTierS(300).build()); // default 300 but custom=800

        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        UserEntity stealerUser = userWithPokemon(STEALER, "pikachu");
        UserEntity victimUser  = userWithPokemon(VICTIM, TARGET);
        when(userRepository.findByUsername(STEALER)).thenReturn(stealerUser);
        when(userRepository.findByUsername(VICTIM)).thenReturn(victimUser);

        String result = handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET));

        // stealPrice used was 800 (custom), not 300 (tier default)
        LeagueMember stealerMember = getMember(league, STEALER);
        LeagueMember victimMember  = getMember(league, VICTIM);
        assertThat(stealerMember.getCoinBalance()).isEqualTo(200);   // 1000-800
        assertThat(victimMember.getCoinBalance()).isEqualTo(1600);   // 0+1600

        // customStealPrice preserved on the transferred pick
        ArgumentCaptor<DraftEntity> draftCaptor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        DraftPick saved = draftCaptor.getValue().getPicks().stream()
                .filter(p -> TARGET.equalsIgnoreCase(p.getPokemonName()))
                .findFirst().orElseThrow();
        assertThat(saved.getCustomStealPrice()).isEqualTo(800);
        assertThat(result).isEqualTo(VICTIM);
    }

    // ── Tier A price used when no customStealPrice ────────────────────────────

    @Test
    void handle_tierAPokemon_insufficientCoins_throws() {
        // tier A price = 200, stealer only has 50 → throws at balance check
        // This ensures case A in priceForTier switch is covered
        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(50, 0);
        league.setSettings(LeagueSettings.builder().priceTierA(200).build());
        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.A);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficientes monedas");
    }

    // ── Tier B — stealer has null pokemons (ternary branch) ───────────────────

    @Test
    void handle_stealerNullPokemons_createsNewList() {
        // Stealer exists but getPokemons()==null → handler must create a new ArrayList
        // Also covers case B in priceForTier switch
        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500);
        league.setSettings(LeagueSettings.builder().priceTierB(100).build());
        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.B);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        UserEntity stealerUser = new UserEntity();
        stealerUser.setName(STEALER);
        stealerUser.setPokemons(null); // ← null pokemons: forces the else-branch

        UserEntity victimUser = userWithPokemon(VICTIM, TARGET);
        when(userRepository.findByUsername(STEALER)).thenReturn(stealerUser);
        when(userRepository.findByUsername(VICTIM)).thenReturn(victimUser);

        String result = handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET));

        // Pokemon was added to the newly-created empty list
        assertThat(stealerUser.getPokemons()).anyMatch(p -> TARGET.equals(p.getName()));
        assertThat(result).isEqualTo(VICTIM);
    }

    // ── Tier C price used ─────────────────────────────────────────────────────

    @Test
    void handle_tierCPokemon_insufficientCoins_throws() {
        // tier C price = 75, stealer only has 10 → covers case C in priceForTier switch
        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(10, 0);
        league.setSettings(LeagueSettings.builder().priceTierC(75).build());
        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.C);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficientes monedas");
    }

    // ── Tier D price used ─────────────────────────────────────────────────────

    @Test
    void handle_tierDPokemon_insufficientCoins_throws() {
        // tier D price = 25, stealer only has 0 → covers case D in priceForTier switch
        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(0, 0);
        league.setSettings(LeagueSettings.builder().priceTierD(25).build());
        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.D);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suficientes monedas");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(StealPokemonCommand.class);
    }

    @Test
    void handle_happyPath_savesActivityEvent() {
        int stealPrice = 300;

        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500);
        league.setSettings(LeagueSettings.builder().priceTierS(stealPrice).build());
        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(userRepository.findByUsername(STEALER)).thenReturn(userWithPokemon(STEALER, "pikachu"));
        when(userRepository.findByUsername(VICTIM)).thenReturn(userWithPokemon(VICTIM, TARGET));

        String result = handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET));

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository).save(captor.capture());
        ActivityEventEntity saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(ActivityEventType.STEAL);
        assertThat(saved.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(saved.getActorUsername()).isEqualTo(STEALER);
        assertThat(saved.getTargetUsername()).isEqualTo(VICTIM);
        assertThat(saved.getPokemonName()).isEqualTo(TARGET);
        assertThat(saved.getCoinsAmount()).isEqualTo(stealPrice);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(result).isEqualTo(VICTIM);
    }

    // ── Timestamp lock ────────────────────────────────────────────────────────

    @Test
    void steal_lockedPokemon_throwsIllegalState() {
        // Pick locked until 1 hour from now → cannot be stolen
        DraftPick pick = new DraftPick(VICTIM, TARGET, 6, 1, Instant.now(), null,
                Instant.now().plus(1, ChronoUnit.HOURS));
        DraftEntity draft = draftWithPick(pick);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bloqueado");
    }

    @Test
    void steal_expiredLock_succeeds() {
        // Pick lock expired 1 hour ago → steal should proceed
        DraftPick pick = new DraftPick(VICTIM, TARGET, 6, 1, Instant.now(), null,
                Instant.now().minus(1, ChronoUnit.HOURS));
        DraftEntity draft = draftWithPick(pick);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500);
        league.setSettings(LeagueSettings.builder().priceTierS(300).build());
        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(userRepository.findByUsername(STEALER)).thenReturn(userWithPokemon(STEALER, "pikachu"));
        when(userRepository.findByUsername(VICTIM)).thenReturn(userWithPokemon(VICTIM, TARGET));

        // Should not throw
        String result = handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET));

        // Pick now belongs to stealer with new lock
        DraftPick stolenPick = draft.getPicks().stream()
                .filter(p -> TARGET.equalsIgnoreCase(p.getPokemonName()))
                .findFirst().orElseThrow();
        assertThat(stolenPick.getUsername()).isEqualTo(STEALER);
        assertThat(stolenPick.getLockedUntil()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        assertThat(result).isEqualTo(VICTIM);
    }

    // ── Push notification ─────────────────────────────────────────────────────

    @Test
    void handle_successfulSteal_sendsNotificationToVictim() {
        int stealPrice = 300;
        DraftEntity draft = completedDraftWithPick(VICTIM, TARGET, 6);
        ScheduleEntity schedule = scheduleWithActiveJornada(1);
        LeagueEntity league = leagueWithTwoMembers(1000, 500);
        league.setSettings(LeagueSettings.builder().priceTierS(stealPrice).build());
        ClosedListEntity entry = closedListEntry(TARGET, 6, Tier.S);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(schedule));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(TARGET, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(jornadaWindowService.isStealWindowOpen(any(), any())).thenReturn(true);

        UserEntity victimUser = new UserEntity();
        victimUser.setName(VICTIM);
        victimUser.setFcmTokens(new ArrayList<>(List.of("token-brock")));
        victimUser.setPokemons(new ArrayList<>());

        UserEntity stealerUser = new UserEntity();
        stealerUser.setName(STEALER);
        stealerUser.setPokemons(new ArrayList<>());

        when(userRepository.findByUsername(VICTIM)).thenReturn(victimUser);
        when(userRepository.findByUsername(STEALER)).thenReturn(stealerUser);

        handler.handle(new StealPokemonCommand(LEAGUE_ID, STEALER, TARGET));

        verify(pushNotificationPort).send(
                eq(List.of("token-brock")),
                eq("Te han robado un Pokémon"),
                anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DraftEntity completedDraftWithPick(String username, String pokemon, int pokemonId) {
        return draftWithPick(new DraftPick(username, pokemon, pokemonId, 1, Instant.now(), null, null));
    }

    private DraftEntity draftWithPick(DraftPick pick) {
        DraftEntity draft = new DraftEntity();
        draft.setId("draft-1");
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(new ArrayList<>(List.of(pick)));
        draft.setTurnOrder(List.of(STEALER, VICTIM));
        return draft;
    }

    /** Minimal schedule — sufficient for JornadaWindowService mock. */
    private ScheduleEntity scheduleWithActiveJornada(int round) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(LEAGUE_ID);
        return schedule;
    }

    /** Schedule where the given jornada has PENDING matches (for lock check). */
    private ScheduleEntity scheduleWithPendingJornada(int round) {
        return scheduleWithActiveJornada(round);
    }

    /** League with stealer (first member) and victim (second member). */
    private LeagueEntity leagueWithTwoMembers(int stealerBalance, int victimBalance) {
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        LeagueMember stealerMember = new LeagueMember(STEALER, LeagueRole.USER, stealerBalance);
        LeagueMember victimMember  = new LeagueMember(VICTIM,  LeagueRole.USER, victimBalance);
        league.setMembers(new ArrayList<>(List.of(stealerMember, victimMember)));
        return league;
    }

    private UserEntity userWithPokemon(String username, String pokemonName) {
        Pokemons p = new Pokemons();
        p.setName(pokemonName);
        p.setLeagueId(LEAGUE_ID);
        UserEntity user = new UserEntity();
        user.setName(username);
        user.setPokemons(new ArrayList<>(List.of(p)));
        return user;
    }

    private ClosedListEntity closedListEntry(String name, int id, Tier tier) {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonName(name);
        entry.setPokemonId(id);
        entry.setLeagueId(LEAGUE_ID);
        entry.setTier(tier);
        return entry;
    }

    private LeagueMember getMember(LeagueEntity league, String username) {
        return league.getMembers().stream()
                .filter(m -> username.equals(m.getUsername()))
                .findFirst().orElseThrow();
    }
}
