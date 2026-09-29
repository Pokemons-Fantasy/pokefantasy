package com.villu.pokefantasy.request.draft;

import com.villu.pokefantasy.dto.Tier;
import lombok.Data;

import java.util.List;

@Data
public class SetDraftPoolTiersRequest {
    private List<String> entryIds;
    private Tier tier;
}
