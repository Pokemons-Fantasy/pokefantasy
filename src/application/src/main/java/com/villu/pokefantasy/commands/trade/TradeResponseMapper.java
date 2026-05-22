package com.villu.pokefantasy.commands.trade;

import com.villu.pokefantasy.repository.entity.TradeEntity;
import com.villu.pokefantasy.response.TradeResponse;

/** Mapea una {@link TradeEntity} a su DTO de API {@link TradeResponse}. Compartido por los handlers de consulta de trades. */
public final class TradeResponseMapper {

    private TradeResponseMapper() {
    }

    public static TradeResponse toResponse(TradeEntity t) {
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
}
