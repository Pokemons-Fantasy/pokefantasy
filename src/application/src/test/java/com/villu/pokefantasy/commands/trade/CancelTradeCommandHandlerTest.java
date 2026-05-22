package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelTradeCommandHandlerTest {

    @Mock private TradeRepository tradeRepository;

    private CancelTradeCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CancelTradeCommandHandler(tradeRepository);
    }

    private TradeEntity pendingTrade() {
        return TradeEntity.builder()
                .id("t1").leagueId("l1").proposer("ash").responder("brock")
                .proposerPokemonName("pikachu").proposerPokemonId(25)
                .responderPokemonName("onix").responderPokemonId(95)
                .coinsOffered(0).status(TradeStatus.PENDING).createdAt(Instant.now())
                .build();
    }

    @Test
    void handle_proposerCancels_marksCancelled() {
        TradeEntity trade = pendingTrade();
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

        handler.handle(new CancelTradeCommand("l1", "t1", "ash"));

        ArgumentCaptor<TradeEntity> captor = ArgumentCaptor.forClass(TradeEntity.class);
        verify(tradeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TradeStatus.CANCELLED);
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
    }

    @Test
    void handle_tradeNotFound_throwsIllegalArgument() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new CancelTradeCommand("l1", "t1", "ash")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trade not found");
    }

    @Test
    void handle_tradeNotPending_throwsIllegalState() {
        TradeEntity trade = pendingTrade();
        trade.setStatus(TradeStatus.ACCEPTED);
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(trade));

        assertThatThrownBy(() -> handler.handle(new CancelTradeCommand("l1", "t1", "ash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pendientes");
    }

    @Test
    void handle_notProposer_throwsForbidden() {
        when(tradeRepository.findById("t1")).thenReturn(Optional.of(pendingTrade()));

        assertThatThrownBy(() -> handler.handle(new CancelTradeCommand("l1", "t1", "brock")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void commandType_returnsCorrectClass() {
        assertThat(handler.commandType()).isEqualTo(CancelTradeCommand.class);
    }
}
