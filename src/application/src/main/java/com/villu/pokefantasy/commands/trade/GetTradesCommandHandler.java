package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetTradesCommandHandler implements CommandHandler<GetTradesCommand, List<TradeResponse>> {

    private final TradeRepository tradeRepository;

    static final int MAX_HISTORY = 200;

    public GetTradesCommandHandler(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public List<TradeResponse> handle(GetTradesCommand command) {
        int limit = Math.clamp(command.historyLimit(), 0, MAX_HISTORY);
        return tradeRepository.findByLeagueIdAndParticipant(command.leagueId(), command.username(), limit)
                .stream()
                .map(TradeResponseMapper::toResponse)
                .toList();
    }

    @Override
    public Class<GetTradesCommand> commandType() {
        return GetTradesCommand.class;
    }
}
