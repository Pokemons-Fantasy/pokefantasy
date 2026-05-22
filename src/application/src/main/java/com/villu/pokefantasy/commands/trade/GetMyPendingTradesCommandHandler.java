package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetMyPendingTradesCommandHandler
        implements CommandHandler<GetMyPendingTradesCommand, List<TradeResponse>> {

    private final TradeRepository tradeRepository;

    public GetMyPendingTradesCommandHandler(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public List<TradeResponse> handle(GetMyPendingTradesCommand command) {
        return tradeRepository.findPendingByResponder(command.username())
                .stream()
                .map(TradeResponseMapper::toResponse)
                .toList();
    }

    @Override
    public Class<GetMyPendingTradesCommand> commandType() {
        return GetMyPendingTradesCommand.class;
    }
}
