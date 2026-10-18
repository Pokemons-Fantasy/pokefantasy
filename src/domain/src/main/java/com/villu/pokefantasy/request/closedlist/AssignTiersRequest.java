package com.villu.pokefantasy.request.closedlist;

import com.villu.pokefantasy.dto.Tier;
import lombok.Data;

import java.util.List;

@Data
public class AssignTiersRequest {
    private List<String> entryIds;
    private Tier tier;
}
