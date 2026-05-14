package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.Stat;
import com.villu.pokefantasy.dto.Tier;
import com.villu.pokefantasy.dto.Type;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "closed_list")
public class ClosedListEntity {
    @Id
    private String id;
    private Integer pokemonId;
    private String pokemonName;
    private Tier tier;
    private List<Stat> stats;
    private List<Type> types;
    private String nominatedBy;
    private String sprite;
    private String leagueId;
}
