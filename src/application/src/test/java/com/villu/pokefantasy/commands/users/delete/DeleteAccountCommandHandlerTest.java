package com.villu.pokefantasy.commands.users.delete;

import com.villu.pokefantasy.commands.users.DeletedUser;
import com.villu.pokefantasy.commands.users.login.LoginUserCommandHandler;
import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import com.villu.pokefantasy.league.LeagueMemberRemoval;
import com.villu.pokefantasy.ports.DeletedUsernamePort;
import com.villu.pokefantasy.ports.LoginAttemptPort;
import com.villu.pokefantasy.ports.PasswordHashPort;
import com.villu.pokefantasy.ports.RefreshTokenPort;
import com.villu.pokefantasy.ports.TokenPort;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.AvatarRepository;
import com.villu.pokefantasy.repository.ClosedListRepository;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.UserRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteAccountCommandHandlerTest {

    private static final String GONE = "Usuario eliminado";

    @Mock private UserRepository userRepository;
    @Mock private PasswordHashPort passwordHashPort;
    @Mock private LoginAttemptPort loginAttemptPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private AvatarRepository avatarRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private LeagueMemberRemoval leagueMemberRemoval;
    @Mock private DraftRepository draftRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private ClosedListRepository closedListRepository;
    @Mock private DeletedUsernamePort deletedUsernamePort;
    @Mock private TokenPort tokenPort;

    private DeleteAccountCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeleteAccountCommandHandler(userRepository, passwordHashPort, loginAttemptPort, refreshTokenPort,
                avatarRepository, leagueRepository, leagueMemberRemoval, draftRepository, scheduleRepository,
                tradeRepository, activityEventRepository, closedListRepository, deletedUsernamePort, tokenPort);
        when(tokenPort.accessTokenTtl()).thenReturn(java.time.Duration.ofMinutes(15));
        UserEntity ash = new UserEntity();
        ash.setName("ash");
        ash.setPassword("hash");
        when(userRepository.findByUsername("ash")).thenReturn(ash);
        when(passwordHashPort.matches("pikachu123", "hash")).thenReturn(true);
        when(leagueRepository.findByMemberUsername("ash")).thenReturn(List.of());
    }

    private static LeagueEntity league(String id, LeagueMember... members) {
        LeagueEntity league = new LeagueEntity();
        league.setId(id);
        league.setCreatedBy("misty");
        league.setMembers(new ArrayList<>(List.of(members)));
        return league;
    }

    private static LeagueMember member(String name, LeagueRole role) {
        return new LeagueMember(name, role, 0);
    }

    /** La liga tal como queda tras sacar a ash (la relee el handler). */
    private void leagueWithOthers(LeagueEntity before, LeagueEntity after) {
        when(leagueRepository.findByMemberUsername("ash")).thenReturn(List.of(before));
        when(leagueRepository.findById(before.getId())).thenReturn(Optional.of(after));
    }

    @Test
    void handle_missingPassword_isBadRequest() {
        assertThatThrownBy(() -> handler.handle(new DeleteAccountCommand("ash", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(leagueMemberRemoval);
    }

    @Test
    void handle_wrongPassword_recordsFailureAndChangesNothing() {
        assertThatThrownBy(() -> handler.handle(new DeleteAccountCommand("ash", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptPort).recordFailure("user:ash", LoginUserCommandHandler.LOCKOUT_WINDOW);
        verifyNoInteractions(leagueMemberRemoval, avatarRepository, refreshTokenPort);
        verify(userRepository, never()).deleteByUsername(any());
    }

    @Test
    void handle_userAtFailureLimit_isTooManyAttempts() {
        when(loginAttemptPort.failureCount("user:ash")).thenReturn((long) LoginUserCommandHandler.MAX_FAILURES_PER_USER);

        assertThatThrownBy(() -> handler.handle(new DeleteAccountCommand("ash", "pikachu123")))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(userRepository, never()).deleteByUsername(any());
    }

    @Test
    void handle_cannotReserveTheName_deletesNothing() {
        doThrow(new IllegalStateException("redis down")).when(deletedUsernamePort).reserve(any(), any());

        assertThatThrownBy(() -> handler.handle(new DeleteAccountCommand("ash", "pikachu123")))
                .isInstanceOf(IllegalStateException.class);
        verify(leagueRepository, never()).findByMemberUsername(any());
        verify(userRepository, never()).deleteByUsername(any());
    }

    @Test
    void handle_userWithoutLeagues_deletesAvatarSessionsAndUser() {
        List<String> remaining = handler.handle(new DeleteAccountCommand("ash", "pikachu123"));

        assertThat(remaining).isEmpty();
        verify(loginAttemptPort).clearFailures("user:ash");
        verify(avatarRepository).deleteByUsername("ash");
        verify(refreshTokenPort).revokeAll("ash");
        verify(userRepository).deleteByUsername("ash");
        verify(deletedUsernamePort).reserve("ash", java.time.Duration.ofMinutes(15));
    }

    @Test
    void handle_leagueWithOthers_removesAnonymizesAndReturnsItsId() {
        LeagueEntity before = league("l1", member("misty", LeagueRole.ADMIN), member("ash", LeagueRole.USER));
        leagueWithOthers(before, league("l1", member("misty", LeagueRole.ADMIN)));

        List<String> remaining = handler.handle(new DeleteAccountCommand("ash", "pikachu123"));

        assertThat(remaining).containsExactly("l1");
        verify(leagueMemberRemoval).remove(before, "ash");
        verify(tradeRepository).cancelPending(eq("l1"), eq("ash"), any(Instant.class));
        verify(tradeRepository).anonymizeUser("l1", "ash", GONE);
        verify(activityEventRepository).anonymizeUser("l1", "ash", GONE);
        verify(leagueRepository, never()).deleteById(any());
    }

    @Test
    void handle_onlyAdmin_firstRemainingMemberBecomesAdmin() {
        LeagueEntity before = league("l1", member("ash", LeagueRole.ADMIN), member("misty", LeagueRole.USER),
                member("brock", LeagueRole.USER));
        leagueWithOthers(before, league("l1", member("misty", LeagueRole.USER), member("brock", LeagueRole.USER)));

        handler.handle(new DeleteAccountCommand("ash", "pikachu123"));

        ArgumentCaptor<LeagueEntity> saved = ArgumentCaptor.forClass(LeagueEntity.class);
        verify(leagueRepository).save(saved.capture());
        assertThat(saved.getValue().getMembers()).extracting(LeagueMember::getLeagueRole)
                .containsExactly(LeagueRole.ADMIN, LeagueRole.USER);
    }

    @Test
    void handle_createdBy_isAnonymized() {
        LeagueEntity before = league("l1", member("misty", LeagueRole.ADMIN), member("ash", LeagueRole.USER));
        LeagueEntity after = league("l1", member("misty", LeagueRole.ADMIN));
        after.setCreatedBy("ash");
        leagueWithOthers(before, after);

        handler.handle(new DeleteAccountCommand("ash", "pikachu123"));

        ArgumentCaptor<LeagueEntity> saved = ArgumentCaptor.forClass(LeagueEntity.class);
        verify(leagueRepository).save(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(GONE);
    }

    @Test
    void handle_onlyMember_deletesTheLeagueAndEverythingInIt() {
        when(leagueRepository.findByMemberUsername("ash"))
                .thenReturn(List.of(league("l1", member("ash", LeagueRole.ADMIN))));
        DraftEntity draft = new DraftEntity();
        when(draftRepository.findAllByLeagueId("l1")).thenReturn(List.of(draft));

        List<String> remaining = handler.handle(new DeleteAccountCommand("ash", "pikachu123"));

        assertThat(remaining).isEmpty();
        verify(leagueRepository).deleteById("l1");
        verify(scheduleRepository).deleteByLeagueId("l1");
        verify(tradeRepository).deleteByLeagueId("l1");
        verify(activityEventRepository).deleteByLeagueId("l1");
        verify(closedListRepository).deleteAllByLeagueId("l1");
        verify(draftRepository).delete(draft);
        verifyNoInteractions(leagueMemberRemoval);
        verify(tradeRepository, never()).anonymizeUser(any(), any(), any());
    }

    @Test
    void handle_matchesDraftsAndNominations_areAnonymized() {
        LeagueEntity before = league("l1", member("misty", LeagueRole.ADMIN), member("ash", LeagueRole.USER));
        leagueWithOthers(before, league("l1", member("misty", LeagueRole.ADMIN)));

        ScheduleEntity.Match played = new ScheduleEntity.Match();
        played.setPlayer1("ash");
        played.setPlayer2("misty");
        played.setWinnerUsername("ash");
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setJornadas(List.of(new ScheduleEntity.Jornada(1, new ArrayList<>(List.of(played)), null)));
        when(scheduleRepository.findByLeagueId("l1")).thenReturn(Optional.of(schedule));

        DraftEntity past = new DraftEntity();
        past.setTurnOrder(new ArrayList<>(List.of("misty", "ash")));
        past.setPicks(new ArrayList<>(List.of(pick("ash", "pikachu"), pick("misty", "staryu"))));
        past.setDraftHistory(new ArrayList<>(List.of(pick("ash", "bulbasaur"))));
        when(draftRepository.findAllByLeagueId("l1")).thenReturn(List.of(past));

        ClosedListEntity nominated = new ClosedListEntity();
        nominated.setNominatedBy("ash");
        ClosedListEntity other = new ClosedListEntity();
        other.setNominatedBy("misty");
        when(closedListRepository.findAllByLeagueId("l1")).thenReturn(List.of(nominated, other));

        handler.handle(new DeleteAccountCommand("ash", "pikachu123"));

        verify(scheduleRepository).save(schedule);
        assertThat(played.getPlayer1()).isEqualTo(GONE);
        assertThat(played.getPlayer2()).isEqualTo("misty");
        assertThat(played.getWinnerUsername()).isEqualTo(GONE);
        verify(draftRepository).save(past);
        assertThat(past.getTurnOrder()).containsExactly("misty", GONE);
        assertThat(past.getPicks()).extracting(DraftPick::getUsername).containsExactly(GONE, "misty");
        assertThat(past.getDraftHistory()).extracting(DraftPick::getUsername).containsExactly(GONE);
        verify(closedListRepository).save(nominated);
        verify(closedListRepository, never()).save(other);
        assertThat(nominated.getNominatedBy()).isEqualTo(GONE);
    }

    @Test
    void deletedUserName_cannotBeAUsername() {
        assertThat(DeletedUser.NAME).isEqualTo(GONE).contains(" ");
    }

    private static DraftPick pick(String user, String pokemon) {
        DraftPick pick = new DraftPick();
        pick.setUsername(user);
        pick.setPokemonName(pokemon);
        return pick;
    }
}
