package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.ActivityEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "activity_events")
@CompoundIndex(def = "{'leagueId': 1, 'createdAt': -1}")
public class ActivityEventEntity {

    @Id
    private String id;
    private String leagueId;
    private ActivityEventType type;
    private String actorUsername;
    private String targetUsername;
    private String pokemonName;
    private String pokemonName2;
    private Integer coinsAmount;
    private String fromTier;
    private String toTier;
    private Integer roundNumber;
    private Instant createdAt;
}
