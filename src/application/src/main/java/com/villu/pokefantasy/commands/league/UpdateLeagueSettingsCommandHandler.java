package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

@Service
public class UpdateLeagueSettingsCommandHandler
        implements CommandHandler<UpdateLeagueSettingsCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;

    public UpdateLeagueSettingsCommandHandler(LeagueRepository leagueRepository,
                                              DraftRepository draftRepository,
                                              LeagueAdminGuard leagueAdminGuard) {
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
    }

    @Override
    public Void handle(UpdateLeagueSettingsCommand command) {
        if (command.coinsPerWin() == null || command.coinsPerLoss() == null) {
            throw new IllegalArgumentException("coinsPerWin and coinsPerLoss are required");
        }
        if (command.coinsPerWin() < 0 || command.coinsPerLoss() < 0) {
            throw new IllegalArgumentException("coinsPerWin and coinsPerLoss must be >= 0");
        }

        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        DraftEntity draft = draftRepository.findLatestByLeagueId(command.leagueId())
                .orElseThrow(() -> new IllegalStateException(
                        "La configuración solo se puede editar tras completar el draft"));
        if (draft.getStatus() != DraftStatus.COMPLETED) {
            throw new IllegalStateException(
                    "La configuración solo se puede editar tras completar el draft");
        }

        league.setSettings(LeagueSettings.builder()
                .coinsPerWin(command.coinsPerWin())
                .coinsPerLoss(command.coinsPerLoss())
                .build());

        leagueRepository.save(league);
        return null;
    }

    @Override
    public Class<UpdateLeagueSettingsCommand> commandType() {
        return UpdateLeagueSettingsCommand.class;
    }
}
