package com.villu.pokefantasy.repository.entity;

import com.villu.pokefantasy.dto.LeagueRole;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LeagueMember {
    private String username;
    private LeagueRole leagueRole;
    private int coinBalance = 0;
    private String customMvpPokemon; // null = usar primer pick del draftHistory

    /** Constructor de compatibilidad — mantiene la firma que usan todos los tests existentes. */
    public LeagueMember(String username, LeagueRole leagueRole, int coinBalance) {
        this.username = username;
        this.leagueRole = leagueRole;
        this.coinBalance = coinBalance;
    }
}
