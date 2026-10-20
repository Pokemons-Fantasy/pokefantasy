package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DraftPickResponse {
    private String username;
    private String pokemonName;
    private Integer pokemonId;
    private int round;
    private Instant pickedAt;
    /** Custom steal price set by owner. null = use priceTierX default. */
    private Integer customStealPrice;
    /** Jornada en la que entró al equipo; bloqueado hasta que tenga todos sus resultados. null = nunca bloqueado. */
    private Integer lockedThroughJornada;
    /** Calculado en el back con el calendario: no se puede robar, tradear, intercambiar ni liberar. */
    private boolean locked;
    /** Monedas pagadas en el draft. null en drafts sin presupuesto. */
    private Integer price;
}
