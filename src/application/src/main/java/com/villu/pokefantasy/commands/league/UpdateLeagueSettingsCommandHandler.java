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
import java.util.Optional;

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
            throw new IllegalArgumentException("Indica las monedas por victoria y por derrota.");
        }
        if (command.coinsPerWin() < 0 || command.coinsPerLoss() < 0) {
            throw new IllegalArgumentException("Las monedas por victoria y por derrota no pueden ser negativas.");
        }
        if (command.priceTierS() == null || command.priceTierA() == null || command.priceTierB() == null
                || command.priceTierC() == null || command.priceTierD() == null) {
            throw new IllegalArgumentException("Indica el precio de todos los tiers.");
        }
        if (command.priceTierS() < 0 || command.priceTierA() < 0 || command.priceTierB() < 0
                || command.priceTierC() < 0 || command.priceTierD() < 0) {
            throw new IllegalArgumentException("Los precios de los tiers no pueden ser negativos.");
        }

        if (command.tierPctS() == null || command.tierPctA() == null || command.tierPctB() == null
                || command.tierPctC() == null || command.tierPctD() == null) {
            throw new IllegalArgumentException("Indica el porcentaje de todos los tiers.");
        }
        if (command.tierPctS() < 0 || command.tierPctA() < 0 || command.tierPctB() < 0
                || command.tierPctC() < 0 || command.tierPctD() < 0) {
            throw new IllegalArgumentException("Los porcentajes de los tiers no pueden ser negativos.");
        }
        int sumPct = command.tierPctS() + command.tierPctA() + command.tierPctB()
                + command.tierPctC() + command.tierPctD();
        if (sumPct != 100) {
            throw new IllegalArgumentException(
                    "Los porcentajes de los tiers deben sumar 100 (ahora suman " + sumPct + ")");
        }

        if (command.pickedTeamSize() != null && (command.pickedTeamSize() < 1 || command.pickedTeamSize() > 6)) {
            throw new IllegalArgumentException("Los Pokémon por combate deben estar entre 1 y 6.");
        }
        if (command.bestOf() != null && command.bestOf() != 1 && command.bestOf() != 3) {
            throw new IllegalArgumentException("El formato de la serie debe ser al mejor de 1 o de 3.");
        }

        LeagueEntity league = leagueAdminGuard.requireLeagueAdmin(command.leagueId(), command.requestingUsername());

        Optional<DraftEntity> latestDraft = draftRepository.findLatestByLeagueId(command.leagueId());
        if (latestDraft.map(d -> d.getStatus() == DraftStatus.IN_PROGRESS).orElse(false)) {
            throw new IllegalStateException("No se pueden cambiar los ajustes con un draft en curso");
        }

        if (command.maxTeamSize() != null && command.maxTeamSize() < 10) {
            throw new IllegalArgumentException("El tamaño máximo del equipo debe ser al menos 10.");
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
                .turnTimerSeconds(command.turnTimerSeconds() != null ? command.turnTimerSeconds() : 0)
                .stealWindowCloseDay(command.stealWindowCloseDay() != null ? command.stealWindowCloseDay() : 4)
                .stealWindowCloseTime(command.stealWindowCloseTime() != null ? command.stealWindowCloseTime() : "23:59")
                .swapWindowCloseDay(command.swapWindowCloseDay() != null ? command.swapWindowCloseDay() : 5)
                .swapWindowCloseTime(command.swapWindowCloseTime() != null ? command.swapWindowCloseTime() : "16:00")
                .battleGameType(command.battleGameType() != null
                        ? command.battleGameType() : LeagueSettings.DEFAULT_BATTLE_GAME_TYPE)
                .pickedTeamSize(command.pickedTeamSize() != null
                        ? command.pickedTeamSize() : LeagueSettings.DEFAULT_PICKED_TEAM_SIZE)
                .teamSheet(command.teamSheet() != null ? command.teamSheet() : LeagueSettings.DEFAULT_TEAM_SHEET)
                .bestOf(command.bestOf() != null ? command.bestOf() : LeagueSettings.DEFAULT_BEST_OF)
                .build());

        leagueRepository.save(league);

        // Recalcular los tiers del pool con los nuevos porcentajes (no hace nada si el pool está vacío).
        // En preparación los tiers los reparte el admin a mano, y con el draft completado fijan precios y
        // cláusulas de la temporada: en ninguno de los dos casos se pisan.
        boolean tiersFixed = latestDraft
                .map(d -> d.getStatus() == DraftStatus.PENDING || d.getStatus() == DraftStatus.COMPLETED)
                .orElse(false);
        if (!tiersFixed) {
            tierAssignmentService.assignTiersToPool(command.leagueId(), league.getSettings());
        }

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
