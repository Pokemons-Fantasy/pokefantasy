package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.response.TradeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMyPendingTradesCommandHandlerTest {

    @Mock private TradeRepository tradeRepository;

    private GetMyPendingTradesCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetMyPendingTradesCommandHandler(tradeRepository);
    }

    @Test
    void handle_mapsEntitiesToResponses() {
        TradeEntity trade = TradeEntity.builder()
                .id("t1").leagueId("l1").proposer("ash").responder("brock")
                .proposerPokemonName("pikachu").proposerPokemonId(25)
                .responderPokemonName("onix").responderPokemonId(95)
                .coinsOffered(100).status(TradeStatus.PENDING)
                .createdAt(Instant.parse("2026-05-22T10:00:00Z")).build();
        when(tradeRepository.findPendingByResponder("brock")).thenReturn(List.of(trade));

        List<TradeResponse> result = handler.handle(new GetMyPendingTradesCommand("brock"));

        assertThat(result).hasSize(1);
        TradeResponse r = result.get(0);
        assertThat(r.getId()).isEqualTo("t1");
        assertThat(r.getLeagueId()).isEqualTo("l1");
        assertThat(r.getProposer()).isEqualTo("ash");
        assertThat(r.getResponder()).isEqualTo("brock");
        assertThat(r.getResponderPokemonName()).isEqualTo("onix");
        assertThat(r.getCoinsOffered()).isEqualTo(100);
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getCreatedAt()).isEqualTo("2026-05-22T10:00:00Z");
        assertThat(r.getResolvedAt()).isNull();
    }

    @Test
    void handle_multipleLeagues_returnsAll() {
        TradeEntity t1 = TradeEntity.builder()
                .id("t1").leagueId("l1").proposer("ash").responder("brock")
                .proposerPokemonName("pikachu").proposerPokemonId(25)
                .responderPokemonName("onix").responderPokemonId(95)
                .coinsOffered(0).status(TradeStatus.PENDING).createdAt(Instant.now()).build();
        TradeEntity t2 = TradeEntity.builder()
                .id("t2").leagueId("l2").proposer("misty").responder("brock")
                .proposerPokemonName("staryu").proposerPokemonId(120)
                .responderPokemonName("geodude").responderPokemonId(74)
                .coinsOffered(50).status(TradeStatus.PENDING).createdAt(Instant.now()).build();
        when(tradeRepository.findPendingByResponder("brock")).thenReturn(List.of(t1, t2));

        List<TradeResponse> result = handler.handle(new GetMyPendingTradesCommand("brock"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TradeResponse::getLeagueId).containsExactly("l1", "l2");
    }

    @Test
    void handle_noTrades_returnsEmptyList() {
        when(tradeRepository.findPendingByResponder("brock")).thenReturn(List.of());
        assertThat(handler.handle(new GetMyPendingTradesCommand("brock"))).isEmpty();
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetMyPendingTradesCommand.class);
    }
}
