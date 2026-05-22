package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.TradeEntity;

import java.util.List;
import java.util.Optional;

public interface TradeRepository {
    TradeEntity save(TradeEntity trade);
    Optional<TradeEntity> findById(String id);
    /** Trades donde el usuario es proposer O responder, en cualquier estado. */
    List<TradeEntity> findByLeagueIdAndParticipant(String leagueId, String username);
    /** Trades PENDING de la liga (para el auto-cancelado de propuestas en conflicto). */
    List<TradeEntity> findPendingByLeagueId(String leagueId);
}
