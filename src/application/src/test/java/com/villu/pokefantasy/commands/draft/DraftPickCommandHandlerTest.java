package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.Pokemons;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.ClosedListEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.DraftPick;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import com.villu.pokefantasy.repository.entity.UserEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DraftPickCommandHandlerTest {

    @Mock private DraftRepository draftRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private ScheduleRepository scheduleRepository;

    private DraftPickCommandHandler handler;

    private static final String LEAGUE_ID = "league-1";
    private static final String USERNAME = "ash";
    private static final String POKEMON = "pikachu";

    @BeforeEach
    void setUp() {
        handler = new DraftPickCommandHandler(draftRepository, closedListRepository, userRepository,
                leagueRepository, scheduleRepository);
    }

    @Test
    void handle_nullCommand_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_blankUsername_throwsIllegalArgument() {
        assertThatThrownBy(() -> handler.handle(new DraftPickCommand("  ", POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handle_noActiveDraft_throwsIllegalState() {
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active draft");
    }

    @Test
    void handle_notCurrentTurn_throwsIllegalState() {
        DraftEntity draft = activeDraft(List.of("brock", USERNAME), 0, 1, new ArrayList<>());
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not your turn");
    }

    @Test
    void handle_userNotFound_throwsIllegalArgument() {
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 1, new ArrayList<>());
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void handle_maxPokemonsReached_throwsIllegalState() {
        List<DraftPick> picks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            picks.add(new DraftPick(USERNAME, "poke" + i, i, 1, Instant.now()));
        }
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 1, picks);
        UserEntity user = new UserEntity();
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void handle_pokemonAlreadyPicked_throwsIllegalArgument() {
        List<DraftPick> picks = new ArrayList<>();
        picks.add(new DraftPick("brock", POKEMON, 25, 1, Instant.now()));
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 1, picks);
        UserEntity user = new UserEntity();
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been picked");
    }

    @Test
    void handle_pokemonNotInClosedList_throwsIllegalArgument() {
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 1, new ArrayList<>());
        UserEntity user = new UserEntity();
        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the closed list");
    }

    @Test
    void handle_optimisticLockFailure_rollsBackAndThrowsIllegalState() {
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 1, new ArrayList<>());
        UserEntity user = new UserEntity();
        user.setPokemons(new ArrayList<>());
        ClosedListEntity entry = closedListEntry(POKEMON, 25);

        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        doThrow(new OptimisticLockingFailureException("conflict"))
                .when(draftRepository).save(any());

        assertThatThrownBy(() -> handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("try your pick again");

        // Pokemon should have been rolled back from user
        verify(userRepository, times(2)).updateUserWithPokemons(user);
    }

    @Test
    void handle_happyPath_addsPokemonAndAdvancesTurn() {
        DraftEntity draft = activeDraft(List.of(USERNAME, "brock"), 0, 1, new ArrayList<>());
        UserEntity user = new UserEntity();
        user.setPokemons(new ArrayList<>());
        ClosedListEntity entry = closedListEntry(POKEMON, 25);

        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        // User updated with the new pokemon
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).updateUserWithPokemons(userCaptor.capture());
        assertThat(userCaptor.getValue().getPokemons())
                .anyMatch(p -> POKEMON.equals(p.getName()) && LEAGUE_ID.equals(p.getLeagueId()));

        // Draft saved with pick recorded and turn advanced
        ArgumentCaptor<DraftEntity> draftCaptor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(draftCaptor.capture());
        DraftEntity saved = draftCaptor.getValue();
        assertThat(saved.getPicks()).hasSize(1);
        assertThat(saved.getPicks().get(0).getUsername()).isEqualTo(USERNAME);
        assertThat(saved.getCurrentTurnIndex()).isEqualTo(1); // advanced to brock
    }

    @Test
    void handle_lastPickOfLastRound_setsStatusCompletedAndInitsDefaultSettings() {
        // 1 player, already at round 10 → next would exceed max so draft completes
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 10, new ArrayList<>());
        UserEntity user = new UserEntity();
        user.setPokemons(new ArrayList<>());
        ClosedListEntity entry = closedListEntry(POKEMON, 25);
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setSettings(null);

        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DraftStatus.COMPLETED);

        // Default settings initialised on the league
        ArgumentCaptor<LeagueEntity> leagueCaptor = ArgumentCaptor.forClass(LeagueEntity.class);
        verify(leagueRepository).save(leagueCaptor.capture());
        assertThat(leagueCaptor.getValue().getSettings()).isNotNull();
        assertThat(leagueCaptor.getValue().getSettings().getCoinsPerWin()).isEqualTo(100);
        assertThat(leagueCaptor.getValue().getSettings().getCoinsPerLoss()).isEqualTo(50);
    }

    @Test
    void handle_lastPickWithExistingSettings_doesNotOverwrite() {
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 10, new ArrayList<>());
        UserEntity user = new UserEntity();
        user.setPokemons(new ArrayList<>());
        ClosedListEntity entry = closedListEntry(POKEMON, 25);
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setSettings(LeagueSettings.builder().coinsPerWin(500).coinsPerLoss(0).build());

        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        // findById is called but save is NOT (settings already present)
        verify(leagueRepository).findById(LEAGUE_ID);
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void handle_endOfRoundNotLastRound_incrementsRoundAndResetsIndex() {
        // 1 player, round 3 → completing round wraps to round 4
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 3, new ArrayList<>());
        UserEntity user = new UserEntity();
        user.setPokemons(new ArrayList<>());
        ClosedListEntity entry = closedListEntry(POKEMON, 25);

        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
        verify(draftRepository).save(captor.capture());
        DraftEntity saved = captor.getValue();
        assertThat(saved.getCurrentRound()).isEqualTo(4);
        assertThat(saved.getCurrentTurnIndex()).isZero();
        assertThat(saved.getStatus()).isEqualTo(DraftStatus.IN_PROGRESS);
    }

    @Test
    void handle_lastPick_generatesLeagueSchedule() {
        // 1 player, round 10 → advancing goes to round 11 which exceeds MAX → draft completes
        DraftEntity draft = activeDraft(List.of(USERNAME), 0, 10, new ArrayList<>());
        UserEntity user = new UserEntity();
        user.setPokemons(new ArrayList<>());
        ClosedListEntity entry = closedListEntry(POKEMON, 25);
        LeagueEntity league = new LeagueEntity();
        league.setId(LEAGUE_ID);
        league.setSettings(null);

        when(draftRepository.findActiveByLeagueId(LEAGUE_ID)).thenReturn(Optional.of(draft));
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);
        when(closedListRepository.findByPokemonNameIgnoreCaseAndLeagueId(POKEMON, LEAGUE_ID))
                .thenReturn(Optional.of(entry));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(league));

        handler.handle(new DraftPickCommand(USERNAME, POKEMON, LEAGUE_ID));

        // Schedule must be saved with jornadas for 2 players (2 jornadas total)
        ArgumentCaptor<ScheduleEntity> scheduleCaptor = ArgumentCaptor.forClass(ScheduleEntity.class);
        verify(scheduleRepository).save(scheduleCaptor.capture());
        ScheduleEntity savedSchedule = scheduleCaptor.getValue();
        assertThat(savedSchedule.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(savedSchedule.getJornadas()).isNotEmpty();
        // 1 player → BYE added → 2 players → 1 round per leg × 2 legs = 2 jornadas, but the
        // BYE match is excluded → 0 real matches per jornada (just 2 empty jornadas structure)
        // What matters is that the schedule is persisted for the league
        assertThat(savedSchedule.getJornadas()).hasSize(2);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(DraftPickCommand.class);
    }

    // --- helpers ---

    private DraftEntity activeDraft(List<String> turnOrder, int turnIndex, int round, List<DraftPick> picks) {
        DraftEntity draft = new DraftEntity();
        draft.setId("draft-1");
        draft.setStatus(DraftStatus.IN_PROGRESS);
        draft.setTurnOrder(turnOrder);
        draft.setCurrentTurnIndex(turnIndex);
        draft.setCurrentRound(round);
        draft.setPicks(picks);
        draft.setLeagueId(LEAGUE_ID);
        return draft;
    }

    private ClosedListEntity closedListEntry(String name, int id) {
        ClosedListEntity entry = new ClosedListEntity();
        entry.setPokemonName(name);
        entry.setPokemonId(id);
        entry.setLeagueId(LEAGUE_ID);
        return entry;
    }
}
