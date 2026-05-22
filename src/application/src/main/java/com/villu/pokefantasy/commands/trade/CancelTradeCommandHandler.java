package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.dto.TradeStatus;
import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CancelTradeCommandHandler implements CommandHandler<CancelTradeCommand, Void> {

    private final TradeRepository tradeRepository;

    public CancelTradeCommandHandler(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public Void handle(CancelTradeCommand command) {
        TradeEntity trade = tradeRepository.findById(command.tradeId())
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + command.tradeId()));

        if (trade.getStatus() != TradeStatus.PENDING) {
            throw new IllegalStateException("Solo se pueden cancelar propuestas pendientes");
        }
        if (!trade.getProposer().equalsIgnoreCase(command.requestingUser())) {
            throw new ForbiddenOperationException("Solo el proponente puede cancelar esta propuesta");
        }

        trade.setStatus(TradeStatus.CANCELLED);
        trade.setResolvedAt(Instant.now());
        tradeRepository.save(trade);
        return null;
    }

    @Override
    public Class<CancelTradeCommand> commandType() {
        return CancelTradeCommand.class;
    }
}
