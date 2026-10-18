package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.dto.LeagueRole;
import com.villu.pokefantasy.dto.Role;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.UserRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.UserEntity;
import com.villu.pokefantasy.response.AdminLeagueResponse;
import com.villu.pokefantasy.response.AdminUserResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminSearchCommandHandlersTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LeagueRepository leagueRepository = mock(LeagueRepository.class);
    private final SearchUsersForAdminCommandHandler users = new SearchUsersForAdminCommandHandler(userRepository);
    private final SearchLeaguesForAdminCommandHandler leagues = new SearchLeaguesForAdminCommandHandler(leagueRepository);

    @Test
    void shortQueries_returnNothingWithoutQuerying() {
        for (String q : new String[] {null, "", " ", "a"}) {
            assertThat(users.handle(new SearchUsersForAdminCommand(q))).isEmpty();
            assertThat(leagues.handle(new SearchLeaguesForAdminCommand(q))).isEmpty();
        }
        verifyNoInteractions(userRepository, leagueRepository);
    }

    @Test
    void users_areMappedWithAvatarAndAdminFlag() {
        UserEntity ash = new UserEntity();
        ash.setName("ash");
        ash.setAvatarVersion(7L);
        ash.setRole(Role.USER);
        UserEntity root = new UserEntity();
        root.setName("ashroot");
        root.setRole(Role.ADMIN);
        when(userRepository.findByUsernamePrefix("ash")).thenReturn(List.of(ash, root));

        assertThat(users.handle(new SearchUsersForAdminCommand(" ash ")))
                .containsExactly(new AdminUserResponse("ash", 7L, false), new AdminUserResponse("ashroot", null, true));
        assertThat(users.commandType()).isEqualTo(SearchUsersForAdminCommand.class);
    }

    @Test
    void leagues_areMappedWithTheirMemberCount() {
        LeagueEntity kanto = new LeagueEntity();
        kanto.setId("l1");
        kanto.setName("Kanto");
        kanto.setMembers(List.of(new LeagueMember("ash", LeagueRole.ADMIN, 0), new LeagueMember("misty", LeagueRole.USER, 0)));
        when(leagueRepository.findByNameContaining("kan", 20)).thenReturn(List.of(kanto));

        assertThat(leagues.handle(new SearchLeaguesForAdminCommand("kan")))
                .containsExactly(new AdminLeagueResponse("l1", "Kanto", 2));
        assertThat(leagues.commandType()).isEqualTo(SearchLeaguesForAdminCommand.class);
    }
}
