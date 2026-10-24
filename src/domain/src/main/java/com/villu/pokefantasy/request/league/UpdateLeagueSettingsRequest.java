package com.villu.pokefantasy.request.league;

import com.villu.pokefantasy.dto.BattleGameType;
import com.villu.pokefantasy.dto.TeamSheet;
import lombok.Data;

@Data
public class UpdateLeagueSettingsRequest {
    private Integer coinsPerWin;
    private Integer coinsPerLoss;
    private Integer priceTierS;
    private Integer priceTierA;
    private Integer priceTierB;
    private Integer priceTierC;
    private Integer priceTierD;
    private String seasonStartDate;  // ISO "YYYY-MM-DD", optional
    private Integer maxTeamSize;     // optional, default 20
    private Integer tierPctS;        // % of pool for S tier (default 20)
    private Integer tierPctA;        // % of pool for A tier (default 20)
    private Integer tierPctB;        // % of pool for B tier (default 20)
    private Integer tierPctC;        // % of pool for C tier (default 20)
    private Integer tierPctD;        // % of pool for D tier (default 20)
    private Integer turnTimerSeconds; // seconds per turn; 0 = disabled
    private Integer stealWindowCloseDay;   // 1-7 (ISO), default 4 (Thursday)
    private String  stealWindowCloseTime;  // "HH:mm", default "23:59"
    private Integer swapWindowCloseDay;    // 1-7 (ISO), default 5 (Friday)
    private String  swapWindowCloseTime;   // "HH:mm", default "16:00"
    private BattleGameType battleGameType; // optional, default DOUBLES
    private Integer pickedTeamSize;        // 1-6, optional, default 4
    private TeamSheet teamSheet;           // optional, default CLOSED
    private Integer bestOf;                // 1 or 3, optional, default 3
}
