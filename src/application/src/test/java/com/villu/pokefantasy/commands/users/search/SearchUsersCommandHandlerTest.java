package com.villu.pokefantasy.commands.users.search;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchUsersCommandHandlerTest {

    private static final String LEAGUE_ID = "l1";
    private static final String ADMIN = "ash";

    @Mock private UserRepository userRepository;
    @Mock private LeagueRepository leagueRepository;

    private SearchUsersCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SearchUsersCommandHandler(userRepository, new LeagueAdminGuard(leagueRepository));
    }

    private UserEntity userWithName(String name) {
        UserEntity u = new UserEntity();
        u.setName(name);
        return u;
    }

    private void leagueWithMembers(LeagueMember... members) {
        LeagueEntity l = new LeagueEntity();
        l.setId(LEAGUE_ID);
        l.setMembers(new ArrayList<>(List.of(members)));
        when(leagueRepository.findById(LEAGUE_ID)).thenReturn(Optional.of(l));
    }

    private void leagueAdministeredByAsh() {
        leagueWithMembers(new LeagueMember(ADMIN, LeagueRole.ADMIN, 0),
                new LeagueMember("misty", LeagueRole.USER, 0));
    }

    @Test
    void handle_withoutLeague_rejectedBeforeSearching() {
        assertThatThrownBy(() -> handler.handle(new SearchUsersCommand("as", null, ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(new SearchUsersCommand("as", " ", ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, leagueRepository);
    }

    @Test
    void handle_requesterIsPlainMember_forbidden() {
        leagueAdministeredByAsh();

        assertThatThrownBy(() -> handler.handle(new SearchUsersCommand("as", LEAGUE_ID, "misty")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(userRepository, never()).findByUsernamePrefix(anyString());
    }

    @Test
    void handle_requesterOutsideLeague_forbidden() {
        leagueAdministeredByAsh();

        assertThatThrownBy(() -> handler.handle(new SearchUsersCommand("as", LEAGUE_ID, "gary")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(userRepository, never()).findByUsernamePrefix(anyString());
    }

    @Test
    void handle_leagueNotFound_rejected() {
        when(leagueRepository.findById("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new SearchUsersCommand("br", "bad", ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).findByUsernamePrefix(anyString());
    }

    @Test
    void handle_nullPrefix_returnsEmpty() {
        leagueAdministeredByAsh();

        assertThat(handler.handle(new SearchUsersCommand(null, LEAGUE_ID, ADMIN))).isEmpty();
        verify(userRepository, never()).findByUsernamePrefix(anyString());
    }

    @Test
    void handle_shortPrefix_returnsEmpty() {
        leagueAdministeredByAsh();

        assertThat(handler.handle(new SearchUsersCommand("a", LEAGUE_ID, ADMIN))).isEmpty();
        verify(userRepository, never()).findByUsernamePrefix(anyString());
    }

    @Test
    void handle_admin_filtersExistingMembers() {
        leagueAdministeredByAsh();
        when(userRepository.findByUsernamePrefix("as")).thenReturn(
                List.of(userWithName("ash"), userWithName("ashe"), userWithName("asteroid")));

        List<String> result = handler.handle(new SearchUsersCommand("as", LEAGUE_ID, ADMIN));

        assertThat(result).containsExactlyInAnyOrder("ashe", "asteroid");
    }

    @Test
    void handle_limitsTo8Results() {
        leagueAdministeredByAsh();
        List<UserEntity> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add(userWithName("user" + i));
        }
        when(userRepository.findByUsernamePrefix("us")).thenReturn(many);

        List<String> result = handler.handle(new SearchUsersCommand("us", LEAGUE_ID, ADMIN));

        assertThat(result).hasSize(SearchUsersCommandHandler.MAX_RESULTS);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(SearchUsersCommand.class);
    }
}
