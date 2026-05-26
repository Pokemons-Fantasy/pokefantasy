package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.DraftStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "draft")
public class DraftEntity {
    @Id
    private String id;
    @Version
    private Long version;
    private DraftStatus status;
    private List<String> turnOrder;
    private int currentTurnIndex;
    private int currentRound;
    private List<DraftPick> picks;
    /** Copia inmutable del draft original: cada pick tal como se eligió, sin las mutaciones
     *  posteriores de robos/swaps/trades. Alimenta el historial del draft. */
    private List<DraftPick> draftHistory;
    private String leagueId;
    /** Timestamp when the current turn started; reset on each pick or auto-pick. */
    private Instant currentTurnStartedAt;
}
