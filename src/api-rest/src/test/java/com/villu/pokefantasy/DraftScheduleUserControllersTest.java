package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.draft.DraftFacade;
import com.villu.pokefantasy.commands.schedule.ScheduleFacade;
import com.villu.pokefantasy.commands.users.UserFacade;
import com.villu.pokefantasy.commands.users.login.LoginResult;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DraftScheduleUserControllersTest extends ControllerTestSupport {

    private final DraftFacade draftFacade = mock(DraftFacade.class);
    private final ScheduleFacade scheduleFacade = mock(ScheduleFacade.class);
    private final UserFacade userFacade = mock(UserFacade.class);
    private final RealtimeNotifier notifier = mock(RealtimeNotifier.class);
    private final SseEmitterRegistry draftRegistry = new SseEmitterRegistry();
    private final UserSseEmitterRegistry userRegistry = new UserSseEmitterRegistry();
    private final MockMvc mvc = mvc(
            new DraftController(draftFacade, draftRegistry, notifier),
            new ScheduleController(scheduleFacade),
            new UserController(userFacade, userRegistry));

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ── Draft ────────────────────────────────────────────────────────────────

    @Test
    void draftActions_notifyWatchers() throws Exception {
        mvc.perform(json(post("/v1/leagues/l1/draft/start"), "{\"turnOrder\":[\"ash\",\"misty\"]}")).andExpect(status().isOk());
        verify(draftFacade).startDraft(List.of("ash", "misty"), "l1", ME);

        mvc.perform(json(post("/v1/leagues/l1/draft/pick"), "{\"pokemonName\":\"pikachu\"}")).andExpect(status().isOk());
        verify(draftFacade).pick(ME, "pikachu", "l1");

        mvc.perform(delete("/v1/leagues/l1/draft")).andExpect(status().isOk());
        verify(draftFacade).cancelDraft("l1", ME);

        mvc.perform(post("/v1/leagues/l1/draft/auto-pick")).andExpect(status().isOk());
        verify(draftFacade).autoPick("l1", ME);

        verify(notifier, org.mockito.Mockito.times(4)).draftUpdated("l1");
    }

    @Test
    void failedPick_notifiesNobody() throws Exception {
        doThrow(new IllegalStateException("not your turn")).when(draftFacade).pick(ME, "pikachu", "l1");

        mvc.perform(json(post("/v1/leagues/l1/draft/pick"), "{\"pokemonName\":\"pikachu\"}")).andExpect(status().isConflict());

        verify(notifier, never()).draftUpdated(anyString());
    }

    @Test
    void draftStatus() throws Exception {
        mvc.perform(get("/v1/leagues/l1/draft")).andExpect(status().isOk());
        verify(draftFacade).getStatus("l1", ME);
    }

    @Test
    void draftEvents_registerForMembersOnly() throws Exception {
        mvc.perform(get("/v1/leagues/l1/draft/events")).andExpect(request().asyncStarted());
        verify(draftFacade).requireDraftWatcher("l1", ME);
        assertThat(draftRegistry.connectionCount("l1")).isEqualTo(1);

        doThrow(new ForbiddenOperationException("no")).when(draftFacade).requireDraftWatcher("l2", ME);
        mvc.perform(get("/v1/leagues/l2/draft/events")).andExpect(status().isForbidden());
        assertThat(draftRegistry.connectionCount("l2")).isZero();
    }

    // ── Calendario ───────────────────────────────────────────────────────────

    @Test
    void schedule_emptyIs204_otherwise200() throws Exception {
        mvc.perform(get("/v1/leagues/l1/schedule")).andExpect(status().isNoContent());

        when(scheduleFacade.getSchedule("l1", ME)).thenReturn(
                com.villu.pokefantasy.response.ScheduleResponse.builder().build());
        mvc.perform(get("/v1/leagues/l1/schedule")).andExpect(status().isOk());
    }

    @Test
    void results_recordCorrectRevert() throws Exception {
        mvc.perform(json(post("/v1/leagues/l1/schedule/matches/m1/result"), "{\"winnerUsername\":\"misty\"}"))
                .andExpect(status().isNoContent());
        verify(scheduleFacade).recordResult("l1", "m1", "misty", ME);

        mvc.perform(json(put("/v1/leagues/l1/schedule/matches/m1/result"), "{\"winnerUsername\":\"brock\"}"))
                .andExpect(status().isNoContent());
        verify(scheduleFacade).correctResult("l1", "m1", "brock", ME);

        mvc.perform(json(put("/v1/leagues/l1/schedule/matches/m1/result"), "{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put("/v1/leagues/l1/schedule/matches/m1/result"), "{\"winnerUsername\":\" \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/v1/leagues/l1/schedule/matches/m1/result")).andExpect(status().isNoContent());
        verify(scheduleFacade).revertResult("l1", "m1", ME);
    }

    @Test
    void standingsAndStats() throws Exception {
        mvc.perform(get("/v1/leagues/l1/standings")).andExpect(status().isOk());
        mvc.perform(get("/v1/leagues/l1/season-stats")).andExpect(status().isOk());
        verify(scheduleFacade).getStandings("l1", ME);
        verify(scheduleFacade).getSeasonStats("l1", ME);
    }

    // ── Usuario ──────────────────────────────────────────────────────────────

    @Test
    void register() throws Exception {
        mvc.perform(json(post("/v1/user"), "{\"username\":\"ash\",\"password\":\"pikachu123\"}")).andExpect(status().isOk());
        verify(userFacade).create("ash", "pikachu123");
    }

    @Test
    void login_setsSessionCookies_usingFirstForwardedIp() throws Exception {
        when(userFacade.login("ash", "pikachu123", "203.0.113.7")).thenReturn(
                new LoginResult("jwt-token", Duration.ofMinutes(15), "refresh-token", Duration.ofDays(30)));

        MvcResult result = mvc.perform(json(post("/v1/user/login"), "{\"username\":\"ash\",\"password\":\"pikachu123\"}")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ash"))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith("jwt=jwt-token;").contains("HttpOnly", "Max-Age=900"));
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith("refresh=refresh-token;").contains("Max-Age=2592000"));
    }

    @Test
    void login_blankForwardedHeader_usesSocketAddress() throws Exception {
        when(userFacade.login("ash", "pikachu123", "127.0.0.1")).thenReturn(
                new LoginResult("jwt-token", Duration.ofMinutes(15), null, Duration.ofDays(30)));

        mvc.perform(json(post("/v1/user/login"), "{\"username\":\"ash\",\"password\":\"pikachu123\"}")
                        .header("X-Forwarded-For", " "))
                .andExpect(status().isOk());
    }

    @Test
    void login_withoutRefreshToken_onlySetsAccessCookie() throws Exception {
        when(userFacade.login("ash", "pikachu123", "127.0.0.1")).thenReturn(
                new LoginResult("jwt-token", Duration.ofMinutes(15), null, Duration.ofDays(30)));

        MvcResult result = mvc.perform(json(post("/v1/user/login"), "{\"username\":\"ash\",\"password\":\"pikachu123\"}"))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie")).singleElement()
                .satisfies(c -> assertThat(c).startsWith("jwt="));
    }

    @Test
    void logout_revokesAndClearsBothCookies() throws Exception {
        MvcResult result = mvc.perform(post("/v1/user/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh", "refresh-token")))
                .andExpect(status().isOk()).andReturn();

        verify(userFacade).logout("refresh-token");
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(c -> assertThat(c).startsWith("jwt=;").contains("Max-Age=0"))
                .anySatisfy(c -> assertThat(c).startsWith("refresh=;").contains("Max-Age=0"));
    }

    @Test
    void searchPushTokenAndEvents() throws Exception {
        when(userFacade.searchUsers("mi", "l1", ME)).thenReturn(List.of("misty"));
        mvc.perform(get("/v1/users/search").param("q", "mi").param("leagueId", "l1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("misty"));

        mvc.perform(json(post("/v1/users/push-token"), "{\"token\":\"fcm-1\"}")).andExpect(status().isOk());
        verify(userFacade).registerPushToken(ME, "fcm-1");

        mvc.perform(get("/v1/users/events")).andExpect(request().asyncStarted());
        assertThat(userRegistry.connectionCount(ME)).isEqualTo(1);
    }
}
