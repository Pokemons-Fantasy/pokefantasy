package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.commands.schedule.JornadaWindowService;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleasePokemonCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private JornadaWindowService jornadaWindowService;
    @Mock private ActivityEventRepository activityEventRepository;

    private ReleasePokemonCommandHandler handler;

    private static final String LEAGUE_ID  = "league-1";
    private static final String USERNAME   = "ash";
    private static final String POKEMON    = "dragonite";
    private static final int    POKEMON_ID = 149;
    private static final int    TIER_PRICE = 100;
    private static final int    REWARD     = 50; // 100 / 2

    @BeforeEach
    void setUp() {
        handler = new ReleasePokemonCommandHandler(
                draftRepository, closedListRepository, leagueRepository, userRepository,
                scheduleRepository, jornadaWindowService, activityEventRepository);

        lenient().when(scheduleRepository.findByLeagueId(LEAGUE_ID))
                .thenReturn(Optional.of(new ScheduleEntity()));
        lenient().when(jornadaWindowService.isSwapWindowOpen(any())).thenReturn(true);
    }

    @Test
    void handle_happyPath_releasesPokemonAndAddsCoins() {
        DraftPick pick = pick(USERNAME, POKEMON, null);
        DraftEntity draft = draftWithPicks(new ArrayList<>(List.of(pick)));
        LeagueEntity league = leagueWithMember(USERNAME, 200);
        UserEntity user = userWithPokemon(USERNAME, POKEMON);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(closedListEntry(POKEMON, Tier.A)));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        handler.handle(new ReleasePokemonCommand(LEAGUE_ID, USERNAME, POKEMON));

        // DraftPick removed
        assertThat(draft.getPicks()).isEmpty();
        verify(draftRepository).save(draft);

        // Coins added to member
        assertThat(league.getMembers().get(0).getCoinBalance()).isEqualTo(200 + REWARD);
        verify(leagueRepository).save(league);

        // Pokemon removed from user document
        assertThat(user.getPokemons()).isEmpty();
        verify(userRepository).updateUserWithPokemons(user);

        // Activity event
        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(activityEventRepository).save(captor.capture());
        ActivityEventEntity event = captor.getValue();
        assertThat(event.getType()).isEqualTo(ActivityEventType.POKEMON_RELEASED);
        assertThat(event.getActorUsername()).isEqualTo(USERNAME);
        assertThat(event.getPokemonName()).isEqualTo(POKEMON);
        assertThat(event.getCoinsAmount()).isEqualTo(REWARD);
    }

    @Test
    void handle_oddTierPrice_rewardIsFloor() {
        // 75 / 2 = 37 (integer division = floor), not 38
        DraftPick pick = pick(USERNAME, POKEMON, null);
        DraftEntity draft = draftWithPicks(new ArrayList<>(List.of(pick)));
        LeagueEntity league = leagueWithMemberAndTierPrice(USERNAME, 200, 75);
        UserEntity user = userWithPokemon(USERNAME, POKEMON);

        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(closedListEntry(POKEMON, Tier.A)));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        handler.handle(new ReleasePokemonCommand(LEAGUE_ID, USERNAME, POKEMON));

        assertThat(league.getMembers().get(0).getCoinBalance()).isEqualTo(200 + 37);
    }

    @Test
    void handle_draftNotCompleted_throwsIllegalState() {
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ReleasePokemonCommand(LEAGUE_ID, USERNAME, POKEMON)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
    }

    @Test
    void handle_swapWindowClosed_throwsIllegalState() {
        DraftEntity draft = draftWithPicks(new ArrayList<>(List.of(pick(USERNAME, POKEMON, null))));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(jornadaWindowService.isSwapWindowOpen(any())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new ReleasePokemonCommand(LEAGUE_ID, USERNAME, POKEMON)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ventana");
    }

    @Test
    void handle_pokemonNotOwned_throwsIllegalArgument() {
        DraftEntity draft = draftWithPicks(new ArrayList<>());
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new ReleasePokemonCommand(LEAGUE_ID, USERNAME, POKEMON)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(POKEMON);
    }

    @Test
    void handle_pokemonLocked_throwsIllegalState() {
        DraftPick locked = pick(USERNAME, POKEMON, Instant.now().plusSeconds(3600));
        DraftEntity draft = draftWithPicks(new ArrayList<>(List.of(locked)));
        when(draftRepository.findLatestByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new ReleasePokemonCommand(LEAGUE_ID, USERNAME, POKEMON)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bloqueado");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private DraftPick pick(String username, String pokemonName, Instant lockedUntil) {
        return new DraftPick(username, pokemonName, POKEMON_ID, 1, Instant.now(), null, lockedUntil);
    }

    private DraftEntity draftWithPicks(List<DraftPick> picks) {
        DraftEntity draft = new DraftEntity();
        draft.setLeagueId(LEAGUE_ID);
        draft.setStatus(DraftStatus.COMPLETED);
        draft.setPicks(picks);
        return draft;
    }

    private LeagueEntity leagueWithMember(String username, int coins) {
        LeagueSettings settings = new LeagueSettings();
        settings.setPriceTierS(TIER_PRICE);
        settings.setPriceTierA(TIER_PRICE);
        settings.setPriceTierB(TIER_PRICE);
        settings.setPriceTierC(TIER_PRICE);
        settings.setPriceTierD(TIER_PRICE);

        LeagueMember member = new LeagueMember();
        member.setUsername(username);
        member.setCoinBalance(coins);

        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setSettings(settings);
        league.setMembers(new ArrayList<>(List.of(member)));
        return league;
    }

    private LeagueEntity leagueWithMemberAndTierPrice(String username, int coins, int tierPrice) {
        LeagueSettings settings = new LeagueSettings();
        settings.setPriceTierS(tierPrice);
        settings.setPriceTierA(tierPrice);
        settings.setPriceTierB(tierPrice);
        settings.setPriceTierC(tierPrice);
        settings.setPriceTierD(tierPrice);

        LeagueMember member = new LeagueMember();
        member.setUsername(username);
        member.setCoinBalance(coins);

        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setSettings(settings);
        league.setMembers(new ArrayList<>(List.of(member)));
        return league;
    }

    private ClosedListEntity closedListEntry(String pokemonName, Tier tier) {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonId(POKEMON_ID);
        entry.setPokemonName(pokemonName);
        entry.setTier(tier);
        entry.setLeagueId(LEAGUE_ID);
        return entry;
    }

    private UserEntity userWithPokemon(String username, String pokemonName) {
        Pokemons p = new Pokemons();
        p.setId(POKEMON_ID);
        p.setName(pokemonName);
        p.setLeagueId(LEAGUE_ID);

        UserEntity user = new UserEntity();
        user.setName(username);
        user.setPokemons(new ArrayList<>(List.of(p)));
        return user;
    }
}
