package com.villu.pokefantasy.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TradeResponse {
    private String id;
    private String leagueId;
    private String proposer;
    private String responder;
    private String proposerPokemonName;
    private int proposerPokemonId;
    private String responderPokemonName;
    private int responderPokemonId;
    private int coinsOffered;
    private String status;        // "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED"
    private String createdAt;     // ISO instant
    private String resolvedAt;    // ISO instant, null mientras PENDING
}
