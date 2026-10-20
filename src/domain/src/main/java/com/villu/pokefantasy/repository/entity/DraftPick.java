package com.villu.pokefantasy.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DraftPick {
    private String username;
    private String pokemonName;
    private Integer pokemonId;
    private int round;
    private Instant pickedAt;
    /** Custom steal price set by the owner spending coins. null = use priceTierX default. Inherited when stolen. Reset to null when returned to bench. */
    private Integer customStealPrice;
    /**
     * Bloqueo antiguo (7 días desde el robo, trade, compra o swap). Ya no se escribe: {@code JornadaLockMigration}
     * lo convierte en {@link #lockedThroughJornada} y lo deja a null.
     */
    private Instant lockedUntil;
    /** Monedas pagadas en el draft por este pick. null en drafts sin presupuesto y en fichajes fuera del draft. */
    private Integer price;
    /**
     * Jornada en la que entró en el equipo (robo, trade, compra o swap): bloqueado hasta que esa jornada tenga
     * todos sus resultados. null = libre.
     */
    private Integer lockedThroughJornada;

    /** Constructor de compatibilidad: todos los usos anteriores al presupuesto del draft (sin precio). */
    public DraftPick(String username, String pokemonName, Integer pokemonId, int round, Instant pickedAt,
                     Integer customStealPrice, Instant lockedUntil) {
        this(username, pokemonName, pokemonId, round, pickedAt, customStealPrice, lockedUntil, null, null);
    }
}
