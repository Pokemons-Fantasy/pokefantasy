package com.villu.pokefantasy.commands.bench;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
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

    private SwapWithBenchCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String USERNAME = "ash";
    private static final String GIVE = "charizard";
    private static final String TAKE = "pikachu";

    @BeforeEach
    void setUp() {
        handler = new SwapWithBenchCommandHandler(draftRepository, closedListRepository, leagueRepository, userRepository);
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
        draft.getPicks().add(new DraftPick(username, pokemon, pokemonId, 1, Instant.now()));
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
}
