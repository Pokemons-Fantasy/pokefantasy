package com.villu.pokefantasy.request.draft;

import lombok.Data;

import java.util.List;

@Data
public class UpdateDraftConfigRequest {
    private Integer budget;
    private Integer priceS;
    private Integer priceA;
    private Integer priceB;
    private Integer priceC;
    private Integer priceD;
    private Boolean snake;
    /** Orden de turnos; null = no cambia. */
    private List<String> turnOrder;
}
