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
    COIN_REVOKED,
    /** Presupuesto del draft que le sobró al jugador y pasó a su saldo; {@code coinsAmount} es lo sumado. */
    DRAFT_COINS,
    /** El dueño subió la cláusula de robo de un Pokémon; {@code coinsAmount} es lo invertido (la cláusula sube el doble). */
    CLAUSE_RAISED
}
