package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.LeagueRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeagueMember {
    private String username;
    private LeagueRole leagueRole;
}
