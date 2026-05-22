package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.TradeRepository;
import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.response.TradeResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetTradesCommandHandler implements CommandHandler<GetTradesCommand, List<TradeResponse>> {

    private final TradeRepository tradeRepository;

    public GetTradesCommandHandler(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public List<TradeResponse> handle(GetTradesCommand command) {
        return tradeRepository.findByLeagueIdAndParticipant(command.leagueId(), command.username())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TradeResponse toResponse(TradeEntity t) {
        return TradeResponse.builder()
                .id(t.getId())
                .leagueId(t.getLeagueId())
                .proposer(t.getProposer())
                .responder(t.getResponder())
                .proposerPokemonName(t.getProposerPokemonName())
                .proposerPokemonId(t.getProposerPokemonId())
                .responderPokemonName(t.getResponderPokemonName())
                .responderPokemonId(t.getResponderPokemonId())
                .coinsOffered(t.getCoinsOffered())
                .status(t.getStatus().name())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .resolvedAt(t.getResolvedAt() != null ? t.getResolvedAt().toString() : null)
                .build();
    }

    @Override
    public Class<GetTradesCommand> commandType() {
        return GetTradesCommand.class;
    }
}
