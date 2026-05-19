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
                .build();
    }

    @Override
    public Class<GetLeagueSettingsCommand> commandType() {
        return GetLeagueSettingsCommand.class;
    }
}
