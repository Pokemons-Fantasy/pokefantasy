package com.villu.pokefantasy.commands.draft;

import com.villu.pokefantasy.commands.schedule.RoundRobinScheduler;
import com.villu.pokefantasy.dto.ActivityEventType;
import com.villu.pokefantasy.dto.LeagueSettings;
import com.villu.pokefantasy.repository.ActivityEventRepository;
import com.villu.pokefantasy.repository.LeagueRepository;
import com.villu.pokefantasy.repository.ScheduleRepository;
import com.villu.pokefantasy.repository.entity.ActivityEventEntity;
import com.villu.pokefantasy.repository.entity.DraftEntity;
import com.villu.pokefantasy.repository.entity.LeagueEntity;
import com.villu.pokefantasy.repository.entity.LeagueMember;
import com.villu.pokefantasy.repository.entity.ScheduleEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Lo que pasa cuando un draft queda COMPLETED (por el último pick posible o porque al expulsar al jugador en
 * turno ya nadie puede elegir): ajustes por defecto si faltan, sobrante del presupuesto al saldo de cada
 * jugador y calendario round-robin (primera + segunda vuelta).
 */
@Service
public class DraftCompletionService {

    private final LeagueRepository leagueRepository;
    private final ScheduleRepository scheduleRepository;
    private final ActivityEventRepository activityEventRepository;
    private final DraftTurnService draftTurnService;

    public DraftCompletionService(LeagueRepository leagueRepository,
                                  ScheduleRepository scheduleRepository,
                                  ActivityEventRepository activityEventRepository,
                                  DraftTurnService draftTurnService) {
        this.leagueRepository = leagueRepository;
        this.scheduleRepository = scheduleRepository;
        this.activityEventRepository = activityEventRepository;
        this.draftTurnService = draftTurnService;
    }

    public void complete(String leagueId, LeagueEntity league, DraftEntity draft) {
        boolean settingsCreated = initLeagueSettingsIfNeeded(league);
        boolean leftoverPaid = payLeftoverBudgets(league, draft);
        if (settingsCreated || leftoverPaid) {
            leagueRepository.save(league);
        }
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setLeagueId(leagueId);
        schedule.setJornadas(RoundRobinScheduler.generate(draft.getTurnOrder()));
        scheduleRepository.save(schedule);
    }

    private boolean initLeagueSettingsIfNeeded(LeagueEntity league) {
        if (league != null && league.getSettings() == null) {
            league.setSettings(LeagueSettings.defaults());
            return true;
        }
        return false;
    }

    /** Lo que le sobra a cada jugador del presupuesto del draft pasa a su saldo de la liga. */
    private boolean payLeftoverBudgets(LeagueEntity league, DraftEntity draft) {
        if (league == null || draft.getConfig() == null) return false;
        Instant now = Instant.now();
        boolean paid = false;
        for (LeagueMember member : league.getMembers()) {
            if (!draft.getTurnOrder().contains(member.getUsername())) continue;
            Integer leftover = draftTurnService.remainingBudget(draft, member.getUsername());
            if (leftover == null || leftover <= 0) continue;
            member.setCoinBalance(member.getCoinBalance() + leftover);
            activityEventRepository.save(ActivityEventEntity.builder()
                    .leagueId(league.getId())
                    .type(ActivityEventType.DRAFT_COINS)
                    .actorUsername(member.getUsername())
                    .coinsAmount(leftover)
                    .createdAt(now)
                    .build());
            paid = true;
        }
        return paid;
    }
}
