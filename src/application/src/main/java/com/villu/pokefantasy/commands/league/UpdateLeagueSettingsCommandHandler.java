package com.villu.pokefantasy.commands.league;

import com.villu.pokefantasy.commands.closedlist.TierAssignmentService;
import com.villu.pokefantasy.dto.DraftStatus;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.league.LeagueAdminGuard;
import com.villu.pokefantasy.mediator.CommandHandler;
import com.villu.pokefantasy.repository.DraftRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class UpdateLeagueSettingsCommandHandler
        implements CommandHandler<UpdateLeagueSettingsCommand, Void> {

    private final LeagueRepository leagueRepository;
    private final DraftRepository draftRepository;
    private final LeagueAdminGuard leagueAdminGuard;
    private final ScheduleRepository scheduleRepository;
    private final TierAssignmentService tierAssignmentService;

    public UpdateLeagueSettingsCommandHandler(LeagueRepository leagueRepository,
                                              DraftRepository draftRepository,
                                              LeagueAdminGuard leagueAdminGuard,
                                              ScheduleRepository scheduleRepository,
                                              TierAssignmentService tierAssignmentService) {
        this.leagueRepository = leagueRepository;
        this.draftRepository = draftRepository;
        this.leagueAdminGuard = leagueAdminGuard;
        this.scheduleRepository = scheduleRepository;
        this.tierAssignmentService = tierAssignmentService;
    }

    @Override
    public Void handle(UpdateLeagueSettingsCommand command) {
        if (command.coinsPerWin() == null || command.coinsPerLoss() == null) {
            throw new IllegalArgumentException("coinsPerWin and coinsPerLoss are required");
        }
        if (command.coinsPerWin() < 0 || command.coinsPerLoss() < 0) {
            throw new IllegalArgumentException("coinsPerWin and coinsPerLoss must be >= 0");
        }
        if (command.priceTierS() == null || command.priceTierA() == null || command.priceTierB() == null
                || command.priceTierC() == null || command.priceTierD() == null) {
            throw new IllegalArgumentException("All tier prices are required");
        }
        if (command.priceTierS() < 0 || command.priceTierA() < 0 || command.priceTierB() < 0
                || command.priceTierC() < 0 || command.priceTierD() < 0) {
            throw new IllegalArgumentException("Tier prices must be >= 0");
        }

        if (command.tierPctS() == null || command.tierPctA() == null || command.tierPctB() == null
                || command.tierPctC() == null || command.tierPctD() == null) {
            throw new IllegalArgumentException("All tier percentages are required");
        }
        if (command.tierPctS() < 0 || command.tierPctA() < 0 || command.tierPctB() < 0
                || command.tierPctC() < 0 || command.tierPctD() < 0) {
            throw new IllegalArgumentException("Tier percentages must be >= 0");
        }
        int sumPct = command.tierPctS() + command.tierPctA() + command.tierPctB()
                + command.tierPctC() + command.tierPctD();
        if (sumPct != 100) {
            throw new IllegalArgumentException(
                    "Tier percentages must sum to 100 (current sum: " + sumPct + ")");
        }

        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        draftRepository.findLatestByLeagueId(command.leagueId()).ifPresent(draft -> {
            if (draft.getStatus() == DraftStatus.IN_PROGRESS) {
                throw new IllegalStateException(
                        "No se pueden cambiar los ajustes con un draft en curso");
            }
        });

        if (command.maxTeamSize() != null && command.maxTeamSize() < 10) {
            throw new IllegalArgumentException("maxTeamSize must be >= 10");
        }

        league.setSettings(LeagueSettings.builder()
                .coinsPerWin(command.coinsPerWin())
                .coinsPerLoss(command.coinsPerLoss())
                .priceTierS(command.priceTierS())
                .priceTierA(command.priceTierA())
                .priceTierB(command.priceTierB())
                .priceTierC(command.priceTierC())
                .priceTierD(command.priceTierD())
                .seasonStartDate(command.seasonStartDate())
                .maxTeamSize(command.maxTeamSize() != null ? command.maxTeamSize() : 20)
                .tierPctS(command.tierPctS())
                .tierPctA(command.tierPctA())
                .tierPctB(command.tierPctB())
                .tierPctC(command.tierPctC())
                .tierPctD(command.tierPctD())
                .build());

        leagueRepository.save(league);

        // Recalculate pool tiers using the new percentages (no-ops if pool is empty)
        tierAssignmentService.assignTiersToPool(command.leagueId(), league.getSettings());

        // If seasonStartDate was set, retroactively assign weekly dates to all jornadas
        if (command.seasonStartDate() != null) {
            scheduleRepository.findByLeagueId(command.leagueId()).ifPresent(schedule -> {
                LocalDate firstDate = LocalDate.parse(command.seasonStartDate());
                schedule.getJornadas().forEach(j -> {
                    int weekIndex = j.getRoundNumber() - 1;
                    j.setStartDate(firstDate.plusWeeks(weekIndex).toString());
                });
                scheduleRepository.save(schedule);
            });
        }

        return null;
    }

    @Override
    public Class<UpdateLeagueSettingsCommand> commandType() {
        return UpdateLeagueSettingsCommand.class;
    }
}
