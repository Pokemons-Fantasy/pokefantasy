package com.villu.pokefantasy.dto;

public enum ActivityEventType {
    STEAL,
    BENCH_SWAP,
    BENCH_PURCHASE,
    POKEMON_RELEASED,
    TRADE_COMPLETED,
    MATCH_RESULT,
    MATCH_RESULT_REVERTED,
    TIER_CHANGE,
    COIN_EARNED,
    /** Monedas retiradas al anular un resultado; {@code coinsAmount} es lo retirado (positivo). */
    COIN_REVOKED
}
