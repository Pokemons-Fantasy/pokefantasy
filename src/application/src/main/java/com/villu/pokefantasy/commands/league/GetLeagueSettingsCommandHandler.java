package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.response.LeagueSettingsResponse;
import org.springframework.stereotype.Service;

@Service
public class GetLeagueSettingsCommandHandler
        implements CommandHandler<GetLeagueSettingsCommand, LeagueSettingsResponse> {

    private final LeagueRepository leagueRepository;

    public GetLeagueSettingsCommandHandler(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @Override
    public LeagueSettingsResponse handle(GetLeagueSettingsCommand command) {
        LeagueEntity league = leagueRepository.findById(command.leagueId())
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + command.leagueId()));

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
                .build();
    }

    @Override
    public Class<GetLeagueSettingsCommand> commandType() {
        return GetLeagueSettingsCommand.class;
    }
}
