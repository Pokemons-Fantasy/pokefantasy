package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.DraftStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "draft")
public class DraftEntity {
    @Id
    private String id;
    private DraftStatus status;
    private List<String> turnOrder;
    private int currentTurnIndex;
    private int currentRound;
    private List<DraftPick> picks;
}
