package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.dto.Type;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClosedListEntryResponse {
    private String id;
    private Integer pokemonId;
    private String pokemonName;
    private Tier tier;
    private List<Stat> stats;
    private List<Type> types;
    private String nominatedBy;
}
