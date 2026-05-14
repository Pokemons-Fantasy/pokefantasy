package com.villu.pokefantasy.response;

import com.villu.pokefantasy.dto.LeagueRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeagueMemberResponse {
    private String username;
    private LeagueRole leagueRole;
}
