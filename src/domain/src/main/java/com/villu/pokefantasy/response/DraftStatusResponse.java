package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.repository.entity.DraftPick;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DraftStatusResponse {
    private String id;
    private DraftStatus status;
    private List<String> turnOrder;
    private String currentTurn;
    private int currentRound;
    private List<DraftPick> picks;
}
