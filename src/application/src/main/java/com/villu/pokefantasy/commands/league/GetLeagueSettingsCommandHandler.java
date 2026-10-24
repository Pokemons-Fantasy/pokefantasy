package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueMembershipGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.response.LeagueSettingsResponse;
import org.springframework.stereotype.Service;

@Service
public class GetLeagueSettingsCommandHandler
        implements CommandHandler<GetLeagueSettingsCommand, LeagueSettingsResponse> {

    private final LeagueRepository leagueRepository;
    private final LeagueMembershipGuard leagueMembershipGuard;

    public GetLeagueSettingsCommandHandler(LeagueRepository leagueRepository, LeagueMembershipGuard leagueMembershipGuard) {
        this.leagueRepository = leagueRepository;
        this.leagueMembershipGuard = leagueMembershipGuard;
    }

    @Override
    public LeagueSettingsResponse handle(GetLeagueSettingsCommand command) {
        leagueMembershipGuard.requireMember(command.leagueId(), command.requestingUsername());

        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("Liga no encontrada"));

        LeagueSettings settings = league.getSettings();
        if (settings == null) {
            settings = LeagueSettings.defaults();
        }

        return LeagueSettingsResponse.builder()
                .coinsPerWin(settings.getCoinsPerWin())
                .coinsPerLoss(settings.getCoinsPerLoss())
                .priceTierS(settings.getPriceTierS())
                .priceTierA(settings.getPriceTierA())
                .priceTierB(settings.getPriceTierB())
                .priceTierC(settings.getPriceTierC())
                .priceTierD(settings.getPriceTierD())
                .seasonStartDate(settings.getSeasonStartDate())
                .maxTeamSize(settings.getMaxTeamSize())
                .tierPctS(settings.getTierPctS() != null ? settings.getTierPctS() : 20)
                .tierPctA(settings.getTierPctA() != null ? settings.getTierPctA() : 20)
                .tierPctB(settings.getTierPctB() != null ? settings.getTierPctB() : 20)
                .tierPctC(settings.getTierPctC() != null ? settings.getTierPctC() : 20)
                .tierPctD(settings.getTierPctD() != null ? settings.getTierPctD() : 20)
                .turnTimerSeconds(settings.getTurnTimerSeconds() != null ? settings.getTurnTimerSeconds() : 0)
                .stealWindowCloseDay(settings.getStealWindowCloseDay())
                .stealWindowCloseTime(settings.getStealWindowCloseTime())
                .swapWindowCloseDay(settings.getSwapWindowCloseDay())
                .swapWindowCloseTime(settings.getSwapWindowCloseTime())
                // Ligas anteriores a los ajustes de combate: no tienen los campos.
                .battleGameType(settings.getBattleGameType() != null
                        ? settings.getBattleGameType() : LeagueSettings.DEFAULT_BATTLE_GAME_TYPE)
                .pickedTeamSize(settings.getPickedTeamSize() != null
                        ? settings.getPickedTeamSize() : LeagueSettings.DEFAULT_PICKED_TEAM_SIZE)
                .teamSheet(settings.getTeamSheet() != null ? settings.getTeamSheet() : LeagueSettings.DEFAULT_TEAM_SHEET)
                .bestOf(settings.getBestOf() != null ? settings.getBestOf() : LeagueSettings.DEFAULT_BEST_OF)
                .build();
    }

    @Override
    public Class<GetLeagueSettingsCommand> commandType() {
        return GetLeagueSettingsCommand.class;
    }
}
