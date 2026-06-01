package com.villu.pokefantasy.commands.users.search;

import com.villu.pokefantasy.dto.LeagueRole;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchUsersCommandHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private LeagueRepository leagueRepository;

    private SearchUsersCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SearchUsersCommandHandler(userRepository, leagueRepository);
    }

    private UserEntity userWithName(String name) {
        UserEntity u = new UserEntity();
        u.setName(name);
        return u;
    }

    private LeagueEntity leagueWithMembers(String id, LeagueMember... members) {
        LeagueEntity l = new LeagueEntity();
        l.setId(id);
        l.setMembers(new ArrayList<>(List.of(members)));
        return l;
    }

    @Test
    void handle_nullPrefix_returnsEmpty() {
        List<String> result = handler.handle(new SearchUsersCommand(null, null));
        assertThat(result).isEmpty();
        verify(userRepository, never()).findByUsernamePrefix(anyString());
    }

    @Test
    void handle_shortPrefix_returnsEmpty() {
        List<String> result = handler.handle(new SearchUsersCommand("a", null));
        assertThat(result).isEmpty();
        verify(userRepository, never()).findByUsernamePrefix(anyString());
    }

    @Test
    void handle_validPrefixNoLeague_returnsAllMatches() {
        when(userRepository.findByUsernamePrefix("as")).thenReturn(
                List.of(userWithName("ash"), userWithName("ashe")));

        List<String> result = handler.handle(new SearchUsersCommand("as", null));

        assertThat(result).containsExactlyInAnyOrder("ash", "ashe");
    }

    @Test
    void handle_validPrefixWithLeague_filtersExistingMembers() {
        when(userRepository.findByUsernamePrefix("as")).thenReturn(
                List.of(userWithName("ash"), userWithName("ashe"), userWithName("asteroid")));
        when(leagueRepository.findById("l1")).thenReturn(Optional.of(
                leagueWithMembers("l1",
                        new LeagueMember("ash", LeagueRole.ADMIN, 0),
                        new LeagueMember("misty", LeagueRole.USER, 0))));

        List<String> result = handler.handle(new SearchUsersCommand("as", "l1"));

        assertThat(result).containsExactlyInAnyOrder("ashe", "asteroid");
        assertThat(result).doesNotContain("ash");
    }

    @Test
    void handle_leagueNotFound_returnsAllMatches() {
        when(userRepository.findByUsernamePrefix("br")).thenReturn(
                List.of(userWithName("brock")));
        when(leagueRepository.findById("bad")).thenReturn(Optional.empty());

        List<String> result = handler.handle(new SearchUsersCommand("br", "bad"));

        assertThat(result).containsExactly("brock");
    }

    @Test
    void handle_limitsTo8Results() {
        List<UserEntity> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add(userWithName("user" + i));
        }
        when(userRepository.findByUsernamePrefix("us")).thenReturn(many);

        List<String> result = handler.handle(new SearchUsersCommand("us", null));

        assertThat(result).hasSize(8);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(SearchUsersCommand.class);
    }
}
