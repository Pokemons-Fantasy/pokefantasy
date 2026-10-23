package com.villu.pokefantasy.repository;

import com.villu.pokefantasy.repository.entity.TradeEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradeRepository {
    TradeEntity save(TradeEntity trade);
    Optional<TradeEntity> findById(String id);
    /** Trades donde el usuario es proposer O responder, en cualquier estado. */
    /** Trades de la liga en los que participa: todos los pendientes y los {@code resolvedLimit} resueltos más recientes. */
    List<TradeEntity> findByLeagueIdAndParticipant(String leagueId, String username, int resolvedLimit);
    /** Trades PENDING de la liga (para el auto-cancelado de propuestas en conflicto). */
    List<TradeEntity> findPendingByLeagueId(String leagueId);
    /** Trades PENDING donde el usuario es el responder, en cualquier liga. */
    List<TradeEntity> findPendingByResponder(String username);

    void deleteByLeagueId(String leagueId);

    /** Los PENDING en los que participa 'username', en cualquier liga, pasan a CANCELLED. */
    void cancelPending(String username, Instant resolvedAt);

    /** Cambia 'username' por 'replacement' como proponente y como destinatario, en cualquier liga. */
    void anonymizeUser(String username, String replacement);
}
