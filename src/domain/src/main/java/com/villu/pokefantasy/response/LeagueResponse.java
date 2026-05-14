package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.LeagueStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeagueResponse {
    private String id;
    private String name;
    private String createdBy;
    private int memberCount;
    private LeagueStatus status;
}
