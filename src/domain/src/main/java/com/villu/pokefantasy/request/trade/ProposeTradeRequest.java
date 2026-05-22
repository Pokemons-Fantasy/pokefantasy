package com.villu.pokefantasy.request.trade;

import lombok.Data;

@Data
public class ProposeTradeRequest {
    private String responder;
    private String proposerPokemonName;
    private String responderPokemonName;
    private Integer coinsOffered;   // nullable -> el controller lo trata como 0
}
