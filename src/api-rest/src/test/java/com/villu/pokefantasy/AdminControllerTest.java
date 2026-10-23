package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.admin.AdminFacade;
import com.villu.pokefantasy.commands.users.UserFacade;
import com.villu.pokefantasy.response.AdminLeagueResponse;
import com.villu.pokefantasy.response.AdminUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La restricción al rol ADMIN la pone SecurityConfig: la cubre AdminIntegrationTest. */
class AdminControllerTest extends ControllerTestSupport {

    private final AdminFacade adminFacade = mock(AdminFacade.class);
    private final UserFacade userFacade = mock(UserFacade.class);
    private final UserSseEmitterRegistry userRegistry = new UserSseEmitterRegistry();
    private final RealtimeNotifier notifier = mock(RealtimeNotifier.class);
    private final MockMvc mvc = mvc(new AdminController(adminFacade, userFacade, userRegistry, notifier));

    @Test
    void searchUsers_returnsTheFacadeResult() throws Exception {
        when(adminFacade.searchUsers("tro")).thenReturn(List.of(new AdminUserResponse("troll", 3L, false)));

        mvc.perform(get("/v1/admin/users").param("q", "tro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("troll"))
                .andExpect(jsonPath("$[0].avatarVersion").value(3))
                .andExpect(jsonPath("$[0].admin").value(false));
    }

    @Test
    void searchLeagues_returnsTheFacadeResult() throws Exception {
        when(adminFacade.searchLeagues("kan")).thenReturn(List.of(new AdminLeagueResponse("l1", "Kanto", 4)));

        mvc.perform(get("/v1/admin/leagues").param("q", "kan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("l1"))
                .andExpect(jsonPath("$[0].members").value(4));
    }

    @Test
    void removeAvatar_204() throws Exception {
        mvc.perform(delete("/v1/admin/users/troll/avatar")).andExpect(status().isNoContent());

        verify(adminFacade).removeAvatar(ME, "troll");
    }

    @Test
    void renameLeague_204() throws Exception {
        mvc.perform(put("/v1/admin/leagues/l1/name").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kanto\"}"))
                .andExpect(status().isNoContent());

        verify(adminFacade).renameLeague(ME, "l1", "Kanto");
    }

    @Test
    void adminDeleteUser_revokesSessionsClosesSseAndNotifies() throws Exception {
        when(adminFacade.deleteUser(ME, "troll")).thenReturn(List.of("l1"));
        userRegistry.register("troll");

        mvc.perform(delete("/v1/admin/users/troll")).andExpect(status().isNoContent());

        verify(userFacade).revokeSessions("troll");
        verify(notifier).draftUpdated("l1");
        assertThat(userRegistry.connectionCount("troll")).isZero();
    }
}
