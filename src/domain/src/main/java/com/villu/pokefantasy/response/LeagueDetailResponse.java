package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.LeagueStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LeagueDetailResponse {
    private String id;
    private String name;
    private String createdBy;
    private List<LeagueMemberResponse> members;
    private LeagueStatus status;
}
