package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.bench.BenchFacade;
import com.villu.pokefantasy.commands.closedlist.ClosedListFacade;
import com.villu.pokefantasy.commands.pokemons.PokemonFacade;
import com.villu.pokefantasy.commands.steal.StealFacade;
import com.villu.pokefantasy.commands.trade.TradeFacade;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GameControllersTest extends ControllerTestSupport {

    private final BenchFacade benchFacade = mock(BenchFacade.class);
    private final ClosedListFacade closedListFacade = mock(ClosedListFacade.class);
    private final PokemonFacade pokemonFacade = mock(PokemonFacade.class);
    private final StealFacade stealFacade = mock(StealFacade.class);
    private final TradeFacade tradeFacade = mock(TradeFacade.class);
    private final RealtimeNotifier notifier = mock(RealtimeNotifier.class);
    private final MockMvc mvc = mvc(
            new BenchController(benchFacade), new ClosedListController(closedListFacade),
            new PkmnController(pokemonFacade), new StealController(stealFacade, notifier, new ObjectMapper()),
            new TradeController(tradeFacade, notifier, new ObjectMapper()), new MyTradesController(tradeFacade));

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void bench() throws Exception {
        when(benchFacade.getBench("l1", ME)).thenReturn(List.of());
        mvc.perform(get("/v1/leagues/l1/bench")).andExpect(status().isOk());

        mvc.perform(json(post("/v1/leagues/l1/bench/swap"), "{\"pokemonToGive\":\"rattata\",\"pokemonToTake\":\"pidgey\"}"))
                .andExpect(status().isOk());
        verify(benchFacade).swap("l1", ME, "rattata", "pidgey");

        mvc.perform(json(post("/v1/leagues/l1/bench/buy"), "{\"pokemonName\":\"eevee\"}")).andExpect(status().isOk());
        verify(benchFacade).buy("l1", ME, "eevee");

        mvc.perform(json(post("/v1/leagues/l1/bench/release"), "{\"pokemonName\":\"magikarp\"}")).andExpect(status().isOk());
        verify(benchFacade).release("l1", ME, "magikarp");
    }

    @Test
    void closedList() throws Exception {
        mvc.perform(json(post("/v1/leagues/l1/closed-list/nominate"), "{\"pokemonName\":\"mew\"}")).andExpect(status().isOk());
        verify(closedListFacade).nominate(ME, "mew", "l1");

        mvc.perform(delete("/v1/leagues/l1/closed-list/nominate/mew")).andExpect(status().isOk());
        verify(closedListFacade).denominate(ME, "mew", "l1");

        mvc.perform(json(put("/v1/leagues/l1/closed-list/e1/tier"), "{\"tier\":\"S\"}")).andExpect(status().isOk());
        mvc.perform(get("/v1/leagues/l1/closed-list")).andExpect(status().isOk());
        verify(closedListFacade).getClosedList("l1", ME);
    }

    @Test
    void availablePokemons() throws Exception {
        when(pokemonFacade.getAvailablePokemons()).thenReturn(List.of());
        mvc.perform(get("/v1/pokemons/available")).andExpect(status().isOk());
    }

    @Test
    void steal_notifiesTheVictim() throws Exception {
        when(stealFacade.steal("l1", ME, "pikachu")).thenReturn("misty");

        mvc.perform(json(post("/v1/leagues/l1/steal"), "{\"targetPokemonName\":\"pikachu\"}")).andExpect(status().isOk());

        verify(notifier).notifyUser(eq("misty"), eq("steal"), argThat(data ->
                data.contains("\"actorUsername\":\"ash\"") && data.contains("\"pokemonName\":\"pikachu\"")));
    }

    @Test
    void failedSteal_notifiesNobody() throws Exception {
        when(stealFacade.steal("l1", ME, "pikachu")).thenThrow(new IllegalStateException("window closed"));

        mvc.perform(json(post("/v1/leagues/l1/steal"), "{\"targetPokemonName\":\"pikachu\"}")).andExpect(status().isConflict());

        verify(notifier, never()).notifyUser(anyString(), anyString(), anyString());
    }

    @Test
    void stealPrice() throws Exception {
        mvc.perform(json(put("/v1/leagues/l1/steal-price"), "{\"pokemonName\":\"pikachu\",\"newPrice\":900}"))
                .andExpect(status().isOk());
        verify(stealFacade).setStealPrice("l1", ME, "pikachu", 900);
    }

    @Test
    void trades_proposeNotifiesResponder_andCoinsDefaultToZero() throws Exception {
        when(tradeFacade.propose("l1", ME, "misty", "pikachu", "staryu", 0)).thenReturn("t1");

        mvc.perform(json(post("/v1/leagues/l1/trades"),
                        "{\"responder\":\"misty\",\"proposerPokemonName\":\"pikachu\",\"responderPokemonName\":\"staryu\"}"))
                .andExpect(status().isOk());

        verify(notifier).notifyUser(eq("misty"), eq("trade-proposed"), argThat(data -> data.contains("\"tradeId\":\"t1\"")));
    }

    @Test
    void trades_listRespondCancelAndMine() throws Exception {
        mvc.perform(get("/v1/leagues/l1/trades")).andExpect(status().isOk());
        verify(tradeFacade).getTrades("l1", ME, 50);
        mvc.perform(get("/v1/leagues/l1/trades").param("history", "5")).andExpect(status().isOk());
        verify(tradeFacade).getTrades("l1", ME, 5);

        mvc.perform(json(post("/v1/leagues/l1/trades/t1/respond"), "{\"accept\":true}")).andExpect(status().isOk());
        verify(tradeFacade).respond("l1", "t1", ME, true);

        mvc.perform(delete("/v1/leagues/l1/trades/t1")).andExpect(status().isOk());
        verify(tradeFacade).cancel("l1", "t1", ME);

        mvc.perform(get("/v1/user/trades/pending")).andExpect(status().isOk());
        verify(tradeFacade).getMyPendingTrades(ME);
    }

    @Test
    void trades_proposeWithCoins() throws Exception {
        mvc.perform(json(post("/v1/leagues/l1/trades"),
                        "{\"responder\":\"misty\",\"proposerPokemonName\":\"pikachu\",\"responderPokemonName\":\"staryu\",\"coinsOffered\":150}"))
                .andExpect(status().isOk());

        verify(tradeFacade).propose("l1", ME, "misty", "pikachu", "staryu", 150);
    }
}
