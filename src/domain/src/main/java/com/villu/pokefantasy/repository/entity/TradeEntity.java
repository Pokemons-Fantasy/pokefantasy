package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.TradeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trades")
@CompoundIndexes({
        @CompoundIndex(def = "{'leagueId': 1, 'status': 1}"),
        // Cubre findPendingByResponder, que filtra por responder+status sin acotar por liga.
        @CompoundIndex(def = "{'responder': 1, 'status': 1}")
})
public class TradeEntity {
    @Id
    private String id;
    private String leagueId;
    private String proposer;
    private String responder;
    private String proposerPokemonName;
    private int proposerPokemonId;
    private String responderPokemonName;
    private int responderPokemonId;
    private int coinsOffered;
    private TradeStatus status;
    private Instant createdAt;
    private Instant resolvedAt;
}
