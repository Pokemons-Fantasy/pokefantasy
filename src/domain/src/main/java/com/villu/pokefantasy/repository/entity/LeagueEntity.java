package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.dto.LeagueStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "leagues")
public class LeagueEntity {
    @Id
    private String id;
    private String name;
    private String createdBy;
    private List<LeagueMember> members = new ArrayList<>();
    private LeagueStatus status;
    private LeagueSettings settings;
}
