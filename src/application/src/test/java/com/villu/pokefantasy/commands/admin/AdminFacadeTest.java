package com.villu.pokefantasy.commands.admin;

import com.villu.pokefantasy.commands.users.avatar.DeleteAvatarCommand;
import com.villu.pokefantasy.mediator.Mediator;
import com.villu.pokefantasy.response.AdminLeagueResponse;
import com.villu.pokefantasy.response.AdminUserResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminFacadeTest {

    private final Mediator mediator = mock(Mediator.class);
    private final AdminFacade facade = new AdminFacade(mediator);

    @Test
    void searches_sendTheirCommands() throws Exception {
        List<AdminUserResponse> users = List.of(new AdminUserResponse("ash", null, false));
        List<AdminLeagueResponse> leagues = List.of(new AdminLeagueResponse("l1", "Kanto", 2));
        when(mediator.send(new SearchUsersForAdminCommand("as"))).thenReturn(users);
        when(mediator.send(new SearchLeaguesForAdminCommand("ka"))).thenReturn(leagues);

        assertThat(facade.searchUsers("as")).isEqualTo(users);
        assertThat(facade.searchLeagues("ka")).isEqualTo(leagues);
    }

    @Test
    void actions_sendTheirCommands() throws Exception {
        when(mediator.send(new AdminDeleteUserCommand("root", "troll"))).thenReturn(List.of("l1"));

        facade.removeAvatar("root", "troll");
        facade.renameLeague("root", "l1", "Kanto");

        verify(mediator).send(new DeleteAvatarCommand("troll"));
        verify(mediator).send(new RenameLeagueCommand("root", "l1", "Kanto"));
        assertThat(facade.deleteUser("root", "troll")).containsExactly("l1");
    }
}
