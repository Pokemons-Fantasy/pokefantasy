package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.activity.ActivityFeedFacade;
import com.villu.pokefantasy.commands.invite.InviteFacade;
import com.villu.pokefantasy.commands.league.LeagueFacade;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeagueControllersTest extends ControllerTestSupport {

    private final LeagueFacade leagueFacade = mock(LeagueFacade.class);
    private final InviteFacade inviteFacade = mock(InviteFacade.class);
    private final ActivityFeedFacade activityFeedFacade = mock(ActivityFeedFacade.class);
    private final MockMvc mvc = mvc(new LeagueController(leagueFacade), new InviteController(inviteFacade),
            new ActivityController(activityFeedFacade));

    @Test
    void createLeague_returns201WithId() throws Exception {
        when(leagueFacade.createLeague("Kanto", ME)).thenReturn("l1");

        mvc.perform(post("/v1/leagues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kanto\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string("l1"));
    }

    @Test
    void addAndRemoveMember() throws Exception {
        mvc.perform(post("/v1/leagues/l1/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"misty\"}"))
                .andExpect(status().isOk());
        verify(leagueFacade).addMember("l1", "misty", ME);

        mvc.perform(delete("/v1/leagues/l1/members/misty")).andExpect(status().isNoContent());
        verify(leagueFacade).removeMember("l1", "misty", ME);
    }

    @Test
    void readEndpoints_passTheRequester() throws Exception {
        when(leagueFacade.getMyLeagues(ME)).thenReturn(List.of());
        when(leagueFacade.getMyCoinBalance("l1", ME)).thenReturn(250);

        mvc.perform(get("/v1/leagues/my")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/v1/leagues/l1")).andExpect(status().isOk());
        mvc.perform(get("/v1/leagues/l1/settings")).andExpect(status().isOk());
        mvc.perform(get("/v1/leagues/l1/my-coins")).andExpect(status().isOk()).andExpect(jsonPath("$.coins").value(250));

        verify(leagueFacade).getLeagueDetail("l1", ME);
        verify(leagueFacade).getSettings("l1", ME);
    }

    @Test
    void updateSettings_mapsEveryField() throws Exception {
        mvc.perform(put("/v1/leagues/l1/settings").contentType(MediaType.APPLICATION_JSON).content("""
                        {"coinsPerWin":100,"coinsPerLoss":50,"priceTierS":500,"priceTierA":400,"priceTierB":300,
                         "priceTierC":200,"priceTierD":100,"seasonStartDate":"2026-10-01","maxTeamSize":10,
                         "tierPctS":20,"tierPctA":20,"tierPctB":20,"tierPctC":20,"tierPctD":20,"turnTimerSeconds":60,
                         "stealWindowCloseDay":5,"stealWindowCloseTime":"16:00",
                         "swapWindowCloseDay":7,"swapWindowCloseTime":"20:00"}"""))
                .andExpect(status().isNoContent());

        verify(leagueFacade).updateSettings("l1", 100, 50, 500, 400, 300, 200, 100, "2026-10-01", 10,
                20, 20, 20, 20, 20, 60, 5, "16:00", 7, "20:00", ME);
    }

    @Test
    void setMyMvp() throws Exception {
        mvc.perform(put("/v1/leagues/l1/my-mvp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pokemonName\":\"pikachu\"}"))
                .andExpect(status().isOk());
        verify(leagueFacade).setMyMvp("l1", ME, "pikachu");
    }

    @Test
    void domainErrors_mapToProblemDetail() throws Exception {
        when(leagueFacade.getLeagueDetail("l1", ME)).thenThrow(new ForbiddenOperationException("not a member"));

        mvc.perform(get("/v1/leagues/l1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invites_generateAndRedeem() throws Exception {
        when(inviteFacade.redeem("tok", ME)).thenReturn("l1");

        mvc.perform(post("/v1/leagues/l1/invite/generate")).andExpect(status().isOk());
        verify(inviteFacade).generateInvite("l1", ME);

        mvc.perform(post("/v1/invite/tok/redeem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagueId").value("l1"));
    }

    @Test
    void activityFeed_wholeLeagueOrOnePlayer() throws Exception {
        mvc.perform(get("/v1/leagues/l1/activity")).andExpect(status().isOk());
        verify(activityFeedFacade).getFeed("l1", 0, 20, ME);

        mvc.perform(get("/v1/leagues/l1/activity").param("username", "misty").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
        verify(activityFeedFacade).getFeedByUser("l1", "misty", 2, 5, ME);

        mvc.perform(get("/v1/leagues/l1/activity").param("username", " ")).andExpect(status().isOk());
        // Un username en blanco equivale a "toda la liga".
        verify(activityFeedFacade, org.mockito.Mockito.times(2)).getFeed(eq("l1"), eq(0), eq(20), eq(ME));
    }
}
