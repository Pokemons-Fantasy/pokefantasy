package com.villu.pokefantasy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Reglas económicas de un draft: presupuesto por jugador, precio de cada tier y orden snake. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DraftConfig {

    /** Monedas de cada jugador para este draft; lo que sobra pasa a su saldo de la liga al terminar. */
    private Integer budget;
    private Integer priceS;
    private Integer priceA;
    private Integer priceB;
    private Integer priceC;
    private Integer priceD;
    /** true = el orden se invierte en las rondas pares. */
    private Boolean snake;

    public static DraftConfig defaults() {
        return DraftConfig.builder()
                .budget(1000)
                .priceS(200).priceA(150).priceB(100).priceC(60).priceD(30)
                .snake(false)
                .build();
    }
}
