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
class GetTradesCommandHandlerTest {

    @Mock private TradeRepository tradeRepository;

    private GetTradesCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetTradesCommandHandler(tradeRepository);
    }

    @Test
    void handle_mapsEntitiesToResponses() {
        TradeEntity trade = TradeEntity.builder()
                .id("t1").leagueId("l1").proposer("ash").responder("brock")
                .proposerPokemonName("pikachu").proposerPokemonId(25)
                .responderPokemonName("onix").responderPokemonId(95)
                .coinsOffered(100).status(TradeStatus.PENDING)
                .createdAt(Instant.parse("2026-05-22T10:00:00Z")).build();
        when(tradeRepository.findByLeagueIdAndParticipant("l1", "ash"))
                .thenReturn(List.of(trade));

        List<TradeResponse> result = handler.handle(new GetTradesCommand("l1", "ash"));

        assertThat(result).hasSize(1);
        TradeResponse r = result.get(0);
        assertThat(r.getId()).isEqualTo("t1");
        assertThat(r.getProposer()).isEqualTo("ash");
        assertThat(r.getResponder()).isEqualTo("brock");
        assertThat(r.getProposerPokemonName()).isEqualTo("pikachu");
        assertThat(r.getProposerPokemonId()).isEqualTo(25);
        assertThat(r.getResponderPokemonName()).isEqualTo("onix");
        assertThat(r.getResponderPokemonId()).isEqualTo(95);
        assertThat(r.getCoinsOffered()).isEqualTo(100);
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getCreatedAt()).isEqualTo("2026-05-22T10:00:00Z");
        assertThat(r.getResolvedAt()).isNull();
    }

    @Test
    void handle_noTrades_returnsEmptyList() {
        when(tradeRepository.findByLeagueIdAndParticipant("l1", "ash"))
                .thenReturn(List.of());

        List<TradeResponse> result = handler.handle(new GetTradesCommand("l1", "ash"));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_resolvedTrade_includesResolvedAt() {
        TradeEntity trade = TradeEntity.builder()
                .id("t2").leagueId("l1").proposer("ash").responder("brock")
                .proposerPokemonName("pikachu").proposerPokemonId(25)
                .responderPokemonName("onix").responderPokemonId(95)
                .coinsOffered(0).status(TradeStatus.ACCEPTED)
                .createdAt(Instant.parse("2026-05-22T10:00:00Z"))
                .resolvedAt(Instant.parse("2026-05-22T12:00:00Z")).build();
        when(tradeRepository.findByLeagueIdAndParticipant("l1", "ash"))
                .thenReturn(List.of(trade));

        List<TradeResponse> result = handler.handle(new GetTradesCommand("l1", "ash"));

        assertThat(result).hasSize(1);
        TradeResponse r = result.get(0);
        assertThat(r.getResolvedAt()).isEqualTo("2026-05-22T12:00:00Z");
        assertThat(r.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(GetTradesCommand.class);
    }
}
